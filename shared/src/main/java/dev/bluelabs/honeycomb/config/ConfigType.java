package dev.bluelabs.honeycomb.config;

/**
 * Every configuration file Honeycomb manages.
 *
 * <p>Files are deliberately separate so a server owner can hand one of them to a
 * colleague without scrolling through a thousand unrelated lines.</p>
 */
public enum ConfigType {

    CONFIG("config.yml", "General plugin behaviour", null),
    TABLIST("tablist.yml", "Tablist header, footer and player list names", "tablist"),
    SCOREBOARDS("scoreboards.yml", "Sidebar scoreboards", "scoreboards"),
    NAMETAGS("nametags.yml", "Nametag prefixes and suffixes", "nametags"),
    BOSSBARS("bossbars.yml", "Bossbar definitions", "bossbars"),
    BELOWNAME("belowname.yml", "Below-name displays", "belowname"),
    HOLOGRAMS("holograms.yml", "Name holograms above player heads", "holograms"),
    SORTING("sorting.yml", "Player sorting and grouping", "sorting"),
    ANIMATIONS("animations.yml", "Animation definitions", "animations"),
    PLACEHOLDERS("placeholders.yml", "Placeholder engine settings", "placeholders"),
    INTEGRATIONS("integrations.yml", "Optional plugin integrations", "integrations"),
    NETWORK("network.yml", "Proxy and network synchronisation", "network"),
    WELCOME("welcome.yml", "Welcome and leave messages", "welcome"),
    MAINTENANCE("maintenance.yml", "Maintenance mode, MOTD and server list", null, "server"),
    MESSAGES("messages.yml", "Command and console messages", "messages");

    private final String fileName;
    private final String description;
    private final String rootKey;
    private final String overrideNamespace;

    ConfigType(String fileName, String description, String rootKey) {
        this(fileName, description, rootKey, rootKey != null ? rootKey : fileName.replace(".yml", ""));
    }

    ConfigType(String fileName, String description, String rootKey, String overrideNamespace) {
        this.fileName = fileName;
        this.description = description;
        this.rootKey = rootKey;
        this.overrideNamespace = overrideNamespace;
    }

    /**
     * @return the top level YAML section this file stores its settings under, or
     *         {@code null} when the file keeps its keys at the root (config.yml)
     */
    public String rootKey() {
        return rootKey;
    }

    /**
     * @return the key panel overrides are stored under in panel.yml. Files that keep
     *         their settings at the root use their own file name, so config.yml
     *         overrides live under {@code config:} and messages.yml under
     *         {@code messages:}
     */
    public String overrideRoot() {
        return overrideNamespace;
    }

    /**
     * @return true when the file wraps its settings in a top level section
     */
    public boolean rooted() {
        return rootKey != null;
    }

    /**
     * @return the file name inside the plugin data folder
     */
    public String fileName() {
        return fileName;
    }

    /**
     * @return a short human description of what the file controls
     */
    public String description() {
        return description;
    }
}
