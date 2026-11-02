package dev.bluelabs.honeycomb.network;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Locale;

/**
 * Which proxy, if any, sits in front of this server.
 *
 * <p>Detection reads the server's own configuration instead of guessing from
 * version strings: Paper records Velocity in {@code config/paper-global.yml} and
 * BungeeCord in {@code spigot.yml}. Both formats are read, so the answer is the
 * same on Paper, Purpur and Spigot.</p>
 */
public enum ProxyType {

    /** No proxy: the network placeholders stay unresolved. */
    NONE("none", "None"),

    /** BungeeCord or a fork such as Waterfall. */
    BUNGEE("bungee", "BungeeCord"),

    /** Velocity with the bungee plugin message channel enabled. */
    VELOCITY("velocity", "Velocity");

    private final String key;
    private final String label;

    ProxyType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * @return the value used in {@code network.yml}
     */
    public String key() {
        return key;
    }

    /**
     * @return the name shown to players and in the panel
     */
    public String label() {
        return label;
    }

    /**
     * Resolves a configured value.
     *
     * @param value {@code auto}, {@code bungee}, {@code velocity} or {@code none}
     * @return the configured type, with {@code auto} resolved by detection
     */
    public static ProxyType of(String value) {
        String normalised = value == null ? "auto" : value.toLowerCase(Locale.ROOT).trim();
        return switch (normalised) {
            case "bungee", "bungeecord", "waterfall" -> BUNGEE;
            case "velocity" -> VELOCITY;
            case "none", "off", "false" -> NONE;
            default -> detect(null);
        };
    }

    /**
     * Works out the proxy from the server configuration.
     *
     * @param plugin plugin instance, used to find the server directory
     * @return the detected proxy, never {@code null}
     */
    public static ProxyType detect(Plugin plugin) {
        if (velocityConfigured(plugin) == Boolean.TRUE) {
            return VELOCITY;
        }
        if (bungeeConfigured(plugin) == Boolean.TRUE) {
            return BUNGEE;
        }
        return NONE;
    }

    private static Boolean velocityConfigured(Plugin plugin) {
        YamlConfiguration paper = read(plugin, "config/paper-global.yml");
        if (paper == null) {
            return null;
        }
        if (paper.getBoolean("proxies.velocity.enabled", false)) {
            return Boolean.TRUE;
        }
        String secret = paper.getString("proxies.velocity.secret", "");
        return secret != null && !secret.isBlank() ? Boolean.TRUE : null;
    }

    private static Boolean bungeeConfigured(Plugin plugin) {
        YamlConfiguration spigot = read(plugin, "spigot.yml");
        if (spigot != null && spigot.getBoolean("settings.bungee-cord", false)) {
            return Boolean.TRUE;
        }
        YamlConfiguration paper = read(plugin, "config/paper-global.yml");
        if (paper != null && paper.getBoolean("proxies.bungee-cord.online-mode", false)) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static YamlConfiguration read(Plugin plugin, String relative) {
        File file = plugin == null
                ? new File(Bukkit.getWorldContainer().getParentFile(), relative)
                : new File(plugin.getServer().getWorldContainer().getParentFile(), relative);
        if (!file.isFile()) {
            return null;
        }
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
