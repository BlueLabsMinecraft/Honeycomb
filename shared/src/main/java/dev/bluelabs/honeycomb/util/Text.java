package dev.bluelabs.honeycomb.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central access point for all text parsing.
 *
 * <p>Every user facing string in Honeycomb is MiniMessage. Config authors
 * occasionally paste legacy {@code &}-colour codes, so a parse failure falls back
 * to the legacy serializer instead of throwing and breaking a display update.</p>
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    /** Colour and decoration tags, each closed by its own name. */
    private static final Set<String> SIMPLE = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
            "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white",
            "bold", "b", "italic", "i", "underlined", "u", "strikethrough", "st",
            "obfuscated", "obf", "reset");

    /** Tags that carry an argument, like gradient or shadow. */
    private static final Set<String> COMPLEX = Set.of(
            "gradient", "rainbow", "shadow", "transition", "color", "colour", "font",
            "keybind", "click", "hover", "insertion", "lang", "score", "selector",
            "nbt", "newline", "br");

    private static final Pattern TAG = Pattern.compile("<(/?)([^<>]{1,64})>");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern ANY_TAG = Pattern.compile("<[^<>]{1,64}>");

    private Text() {
    }

    /**
     * Makes a template safe to parse, so both {@code <yellow>hi</yellow>} and
     * {@code <yellow>hi} render yellow.
     *
     * <p>Three jobs: ampersand codes become tags, tags the server does not know are
     * dropped instead of failing the whole line, and every tag left open is closed
     * at the end. A missing closing tag is the most common thing an admin types,
     * and it used to end the colour at the end of the row.</p>
     *
     * @param input raw template
     * @return the sanitised template
     */
    public static String sanitize(String input) {
        String source = LEGACY.matcher(input).replaceAll(match -> "<" + legacyName(match.group(1)) + ">");
        StringBuilder out = new StringBuilder(source.length() + 16);
        Deque<String> open = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(source);
        int last = 0;
        while (matcher.find()) {
            out.append(source, last, matcher.start());
            last = matcher.end();
            boolean closing = !matcher.group(1).isEmpty();
            String body = matcher.group(2).trim();
            String name = body.split(":")[0].toLowerCase(Locale.ROOT);
            boolean known = name.startsWith("#") || SIMPLE.contains(name) || COMPLEX.contains(name);
            if (!known) {
                // Not MiniMessage: drop it instead of printing the markup.
                continue;
            }
            if (closing) {
                // A closing tag with nothing open describes nothing, and MiniMessage
                // rejects it. Drop it, and close anything still nested inside first.
                if (open.contains(name)) {
                    while (!open.isEmpty()) {
                        String top = open.pop();
                        out.append("</").append(top).append('>');
                        if (top.equals(name)) {
                            break;
                        }
                    }
                }
                continue;
            }
            out.append('<').append(body).append('>');
            if (!name.startsWith("#")) {
                open.push(name);
            }
        }
        out.append(source, last, source.length());
        while (!open.isEmpty()) {
            out.append("</").append(open.pop()).append('>');
        }
        return out.toString();
    }

    /** Removes every tag, so a failure can still show readable text. */
    public static String plain(String input) {
        return input == null ? "" : ANY_TAG.matcher(input).replaceAll("");
    }

    private static String legacyName(String code) {
        return switch (Character.toLowerCase(code.charAt(0))) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            default -> "white";
        };
    }

    /**
     * Parses MiniMessage input into a component.
     *
     * @param input raw text, may be {@code null}
     * @return the parsed component, never {@code null}
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(sanitize(input));
        } catch (RuntimeException unsupported) {
            // A tag the running server predates: show the words, never the markup.
            try {
                return MINI_MESSAGE.deserialize(plain(sanitize(input)));
            } catch (RuntimeException ignored) {
                return LEGACY_AMPERSAND.deserialize(plain(input));
            }
        }
    }

    /**
     * Serialises a component with section sign colour codes. Only used where an
     * API still expects a {@link String} instead of a component.
     *
     * @param component the component to serialise
     * @return the legacy string representation
     */
    public static String toLegacy(Component component) {
        return LEGACY_SECTION.serialize(component == null ? Component.empty() : component);
    }

    /**
     * Removes MiniMessage tags, leaving the plain characters.
     *
     * @param input raw text
     * @return the text without tags
     */
    public static String stripTags(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            return MINI_MESSAGE.stripTags(input);
        } catch (RuntimeException unsupported) {
            return plain(input);
        }
    }

    /**
     * Escapes MiniMessage tags so user provided text is shown literally.
     *
     * @param input raw text
     * @return the escaped text
     */
    public static String escape(String input) {
        return input == null ? "" : MINI_MESSAGE.escapeTags(input);
    }
}
