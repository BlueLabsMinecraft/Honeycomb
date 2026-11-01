package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * One YAML file inside {@code plugins/Honeycomb}.
 *
 * <p>Loading a file never destroys user data: existing files are read as they
 * are, missing keys are added from the bundled defaults (after taking a backup),
 * and a file that fails to parse keeps its previous in-memory contents.</p>
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final ConfigType type;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    ConfigFile(Plugin plugin, ConfigType type) {
        this.plugin = plugin;
        this.type = type;
        this.file = new File(plugin.getDataFolder(), type.fileName());
    }

    /**
     * Loads (creating when absent) the file and merges in any newly added
     * default keys.
     *
     * @param report report that receives warnings and errors
     * @return true when the file is usable
     */
    public boolean load(ValidationReport report) {
        String source = type.fileName();
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            report.error(source, "Could not create the plugin data folder");
            return false;
        }
        if (!file.exists() && !createFromDefaults(report)) {
            return false;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException exception) {
            report.error(source, "Could not read file: " + exception.getMessage());
            return false;
        } catch (InvalidConfigurationException exception) {
            report.error(source, "Invalid YAML, keeping previous values: " + exception.getMessage());
            return false;
        }

        this.yaml = loaded;
        mergeDefaults(report);
        return true;
    }

    private boolean createFromDefaults(ValidationReport report) {
        String source = type.fileName();
        if (plugin.getResource(source) == null) {
            report.warning(source, "No bundled default found, creating an empty file");
            try {
                Files.writeString(file.toPath(), "# Honeycomb " + type.description() + "\n", StandardCharsets.UTF_8);
                return true;
            } catch (IOException exception) {
                report.error(source, "Could not create file: " + exception.getMessage());
                return false;
            }
        }
        // saveResource copies the commented default straight from the jar.
        plugin.saveResource(source, false);
        report.info(source, "Created from bundled defaults");
        return file.exists();
    }

    /**
     * Adds keys that exist in the bundled defaults but not in the user's file, so
     * new settings always have a documented value without touching custom ones.
     */
    private void mergeDefaults(ValidationReport report) {
        YamlConfiguration defaults = bundledDefaults();
        if (defaults == null) {
            return;
        }
        List<String> added = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || yaml.contains(key)) {
                continue;
            }
            yaml.set(key, defaults.get(key));
            added.add(key);
        }
        if (added.isEmpty()) {
            return;
        }
        report.info(type.fileName(), "Added " + added.size() + " new default setting(s) from this version");
        backup(report);
        save(report);
    }

    private YamlConfiguration bundledDefaults() {
        if (plugin.getResource(type.fileName()) == null) {
            return null;
        }
        try (InputStream stream = plugin.getResource(type.fileName())) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.load(reader);
                return defaults;
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Bundled default for " + type.fileName() + " could not be read", exception);
            return null;
        }
    }

    /**
     * Copies the current file to {@code filename.bak} before it is rewritten.
     */
    private void backup(ValidationReport report) {
        if (!file.exists()) {
            return;
        }
        File backup = new File(file.getParentFile(), type.fileName() + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            report.warning(type.fileName(), "Could not write backup: " + exception.getMessage());
        }
    }

    /**
     * Writes the in-memory configuration back to disk. Always call this off the
     * main thread when it happens outside startup.
     *
     * @param report report that receives errors
     * @return true when the write succeeded
     */
    public boolean save(ValidationReport report) {
        try {
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            report.error(type.fileName(), "Could not save file: " + exception.getMessage());
            return false;
        }
    }

    /**
     * @return the live configuration
     */
    public YamlConfiguration yaml() {
        return yaml;
    }

    /**
     * @return the underlying file
     */
    public File file() {
        return file;
    }

    /**
     * @return the configuration type
     */
    public ConfigType type() {
        return type;
    }

    /**
     * Reads a section, returning an empty section when it is missing so callers
     * never have to null check the result.
     *
     * @param path section path
     * @return the section, never {@code null}
     */
    public ConfigurationSection section(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section != null) {
            return section;
        }
        // Detached empty section: reading a missing path must not write to the file.
        YamlConfiguration detached = new YamlConfiguration();
        return detached.createSection("empty");
    }
}
