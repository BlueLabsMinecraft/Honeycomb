package dev.bluelabs.honeycomb.config;

import dev.bluelabs.honeycomb.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Message lookup for {@code messages.yml}.
 *
 * <p>Keys are MiniMessage. A missing key is reported once and rendered as a clear
 * placeholder instead of throwing, so a broken message file can never take a
 * command down.</p>
 */
public final class Messages {

    private final ConfigManager configManager;
    private final ValidationReport report;
    private final Set<String> reportedMissing = new HashSet<>();

    /**
     * @param configManager configuration manager
     * @param report        report that receives missing key warnings
     */
    public Messages(ConfigManager configManager, ValidationReport report) {
        this.configManager = configManager;
        this.report = report;
    }

    private YamlConfiguration yaml() {
        return configManager.yaml(ConfigType.MESSAGES);
    }

    /**
     * @return the configured chat prefix
     */
    public String prefix() {
        return yaml().getString("prefix", "<gradient:#FADE29:#D56B2B><bold>Honeycomb</bold></gradient> <dark_gray>»</dark_gray> ");
    }

    /**
     * Reads a raw message.
     *
     * @param key message path
     * @return the raw MiniMessage string
     */
    public String raw(String key) {
        String value = yaml().getString(key);
        if (value != null) {
            return value;
        }
        if (reportedMissing.add(key)) {
            report.warning("messages.yml", "Missing message key '" + key + "'");
        }
        return "<gray>" + humanise(key);
    }

    /**
     * Turns a dotted key into words, so a missing message never prints
     * {@code command.status-line} to a player.
     *
     * @param key message path
     * @return readable text
     */
    private static String humanise(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1).replace('-', ' ').replace('_', ' ').trim();
        if (leaf.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(leaf.charAt(0)) + leaf.substring(1);
    }

    /**
     * Renders a message with {@code {placeholder}} replacements applied before
     * MiniMessage parsing.
     *
     * @param key          message path
     * @param replacements replacement values, keyed without braces
     * @return the rendered component
     */
    public Component component(String key, Map<String, String> replacements) {
        String raw = raw(key);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return Text.parse(raw);
    }

    /**
     * Renders a message with the chat prefix in front.
     *
     * @param key          message path
     * @param replacements replacement values
     * @return the prefixed component
     */
    public Component prefixed(String key, Map<String, String> replacements) {
        return Text.parse(prefix()).append(component(key, replacements));
    }

    /**
     * Renders a message without replacements.
     *
     * @param key message path
     * @return the rendered component
     */
    public Component component(String key) {
        return component(key, Map.of());
    }

    /**
     * Sends a prefixed message to a command sender.
     *
     * @param sender       recipient
     * @param key          message path
     * @param replacements replacement values
     */
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(prefixed(key, replacements));
    }

    /**
     * Sends a prefixed message without replacements.
     *
     * @param sender recipient
     * @param key    message path
     */
    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /**
     * Builds a single entry replacement map. Small helper to keep call sites
     * readable.
     *
     * @param key   replacement key
     * @param value replacement value
     * @return a mutable map with one entry
     */
    public static Map<String, String> of(String key, String value) {
        Map<String, String> map = new HashMap<>(2);
        map.put(key, value);
        return map;
    }
}
