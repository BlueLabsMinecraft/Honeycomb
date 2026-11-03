package dev.bluelabs.honeycomb.web;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Turns the web editor's payload into configuration, and back.
 *
 * <p>The web editor is a static page, so there is no server to talk to: it packs the
 * settings you changed into JSON, deflates them and writes the result as base64url.
 * An admin pastes that string after {@code /hc web} and the plugin unpacks it here.
 * Deflate raw is used on both sides because the browser's compression stream and
 * {@link Deflater} both speak it with no headers to disagree about, and SnakeYAML
 * already ships with the server, so JSON needs no extra library.</p>
 */
public final class WebCodec {

    /** Prefix so an old or unrelated string is rejected before it is decoded. */
    public static final String PREFIX = "hc1-";

    /** Codes are pasted into chat, so they stay small and are capped here. */
    private static final int MAX_CODE = 64 * 1024;

    private WebCodec() {
    }

    /**
     * Encodes a payload, used by tests and by {@code /hc web export}.
     *
     * @param payload settings
     * @return the code
     */
    public static String encode(Map<String, Object> payload) {
        String json = toJson(payload);
        byte[] packed = deflate(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
    }

    /**
     * Decodes a code into its sections.
     *
     * @param code text pasted by an admin, with or without the prefix
     * @return section name to settings, never {@code null}
     * @throws IllegalArgumentException when the code is malformed or too large
     */
    public static Map<String, Object> decode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("empty code");
        }
        String body = code.trim();
        if (body.startsWith(PREFIX)) {
            body = body.substring(PREFIX.length());
        }
        if (body.length() > MAX_CODE) {
            throw new IllegalArgumentException("code is too long");
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(pad(body));
        } catch (IllegalArgumentException broken) {
            throw new IllegalArgumentException("that is not a Honeycomb code");
        }
        String json = new String(inflate(packed), StandardCharsets.UTF_8);
        Object parsed;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(MAX_CODE * 8);
            parsed = new Yaml(new SafeConstructor(options)).load(json);
        } catch (RuntimeException broken) {
            throw new IllegalArgumentException("the code did not contain readable settings");
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("the code did not contain settings");
        }
        Map<String, Object> sections = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sections.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return sections;
    }

    /**
     * Flattens one section into the dotted paths panel.yml uses.
     *
     * @param section nested settings
     * @return path to value, lists and scalars kept as they are
     */
    public static Map<String, Object> flatten(Map<?, ?> section) {
        Map<String, Object> out = new LinkedHashMap<>();
        walk("", section, out);
        return out;
    }

    private static void walk(String prefix, Map<?, ?> section, Map<String, Object> out) {
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                walk(path, nested, out);
            } else {
                out.put(path, value);
            }
        }
    }

    private static String pad(String body) {
        int remainder = body.length() % 4;
        return remainder == 0 ? body : body + "=".repeat(4 - remainder);
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                out.write(buffer, 0, written);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] input) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(input);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int written = inflater.inflate(buffer);
                if (written == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buffer, 0, written);
                if (out.size() > MAX_CODE * 16) {
                    throw new IllegalArgumentException("code expands too far");
                }
            }
            return out.toByteArray();
        } catch (DataFormatException broken) {
            throw new IllegalArgumentException("that code is damaged");
        } finally {
            inflater.end();
        }
    }

    /** Small JSON writer: the payload is maps, lists, strings, numbers and booleans. */
    private static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out);
            }
            out.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else {
            quote(String.valueOf(value), out);
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }
}
