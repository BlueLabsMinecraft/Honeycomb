package dev.bluelabs.honeycomb.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Owns every Honeycomb configuration file and the world override folder.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final Map<ConfigType, ConfigFile> files = new EnumMap<>(ConfigType.class);
    private final ValidationReport report = new ValidationReport();
    private final File worldsFolder;
    private PanelOverrides overrides;

    /**
     * @param plugin plugin instance owning the data folder
     */
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.worldsFolder = new File(plugin.getDataFolder(), "worlds");
        for (ConfigType type : ConfigType.values()) {
            files.put(type, new ConfigFile(plugin, type));
        }
    }

    /**
     * Loads every configuration file, creating missing ones with documented
     * defaults.
     *
     * @return the report for this load
     */
    public ValidationReport loadAll() {
        report.clear();
        for (ConfigFile file : files.values()) {
            file.load(report);
            applyOverrides(file);
        }
        ensureFolders();
        return report;
    }

    /**
     * Re-reads every file from disk. Previous values are kept for files that fail
     * to parse, so a reload can never leave the plugin without configuration.
     *
     * @return the report for this reload
     */
    public ValidationReport reloadAll() {
        return loadAll();
    }

    private void applyOverrides(ConfigFile file) {
        if (overrides == null) {
            return;
        }
        overrides.apply(file.type(), file.yaml());
    }

    private void ensureFolders() {
        if (!worldsFolder.exists() && !worldsFolder.mkdirs()) {
            report.warning("worlds/", "Could not create the worlds folder");
        }
        File logs = new File(plugin.getDataFolder(), "logs");
        if (!logs.exists() && !logs.mkdirs()) {
            report.warning("logs/", "Could not create the logs folder");
        }
    }

    /**
     * @param type configuration file
     * @return the managed file
     */
    /**
     * Panel edits live in their own file so the commented defaults are never
     * rewritten. They are merged on top of every loaded config.
     *
     * @param overrides override store, may be {@code null}
     */
    public void useOverrides(PanelOverrides overrides) {
        this.overrides = overrides;
    }

    public ConfigFile file(ConfigType type) {
        return files.get(type);
    }

    /**
     * @param type configuration file
     * @return the live YAML contents of that file
     */
    public org.bukkit.configuration.file.YamlConfiguration yaml(ConfigType type) {
        return files.get(type).yaml();
    }

    /**
     * @return the folder holding per-world override files
     */
    public File worldsFolder() {
        return worldsFolder;
    }

    /**
     * @return the report produced by the most recent load
     */
    public ValidationReport report() {
        return report;
    }

    /**
     * @return every managed configuration file, keyed by type
     */
    public Map<ConfigType, ConfigFile> files() {
        return Collections.unmodifiableMap(files);
    }
}
