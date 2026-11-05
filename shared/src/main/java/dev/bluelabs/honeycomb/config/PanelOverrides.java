package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Values written by the in-game config panel.
 *
 * <p>The shipped YAML files carry long explanatory comments, and a normal
 * {@code YamlConfiguration#save} would throw all of them away. So the panel never
 * edits those files: it stores its values in {@code panel.yml} and they are merged
 * on top of the loaded configuration every time it is read.</p>
 *
 * <p>Deleting an entry from {@code panel.yml} restores the value from the main
 * configuration file, and deleting the file restores every default.</p>
 */
public final class PanelOverrides {

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    public PanelOverrides(Plugin plugin) {
        this(new File(plugin.getDataFolder(), "panel.yml"), plugin);
    }

    /**
     * Test friendly constructor.
     *
     * @param file   override file location
     * @param plugin owning plugin, may be {@code null} in tests
     */
    public PanelOverrides(File file, Plugin plugin) {
        this.file = file;
        this.plugin = plugin;
    }

    /**
     * Reads {@code panel.yml} if it exists. A parse failure keeps the previous
     * values instead of wiping the server's settings.
     */
    public void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        try {
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(file);
            yaml = loaded;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            warn("Could not read panel.yml, keeping the previous values: " + exception.getMessage());
        }
    }

    /**
     * Copies every override for a file on top of its freshly loaded values.
     *
     * @param type   configuration file being loaded
     * @param target the file's live YAML
     */
    public void apply(ConfigType type, YamlConfiguration target) {
        ConfigurationSection section = yaml.getConfigurationSection(type.overrideRoot());
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            // Rooted files keep the section name, root-level files (config.yml,
            // messages.yml) address their keys directly.
            target.set(type.rooted() ? type.rootKey() + "." + key : key, section.get(key));
        }
    }

    /**
     * Stores an override using the path the configuration file itself uses.
     *
     * @param type  file being edited
     * @param path  path without the root section, for example {@code header}
     * @param value new value, {@code null} removes the override
     */
    public void set(ConfigType type, String path, Object value) {
        // The panel passes full paths such as "scoreboards.pvp.enabled" or "tablist.header".
        // Prefixing the file's own root again would store "scoreboards.scoreboards.pvp" in
        // panel.yml: it still applied, but it read like a mistake and made the file hard to
        // check by hand.
        String root = type.overrideRoot();
        String full = path.equals(root) || path.startsWith(root + ".") ? path : root + "." + path;
        set(full, value);
    }

    /**
     * @param path  dotted path including the root section, for example
     *              {@code tablist.header}
     * @param value value to store, {@code null} removes the override
     */
    public void set(String path, Object value) {
        if (value == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path, value);
    }

    /**
     * @param path dotted path
     * @return true when the panel has overridden this value
     */
    public boolean has(String path) {
        return yaml.isSet(path);
    }

    /**
     * Writes the file, keeping one backup of the previous contents.
     *
     * @throws IOException when the file cannot be written
     */
    public void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        if (file.exists()) {
            File backup = new File(parent, "panel.yml.bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                warn("Could not back up panel.yml: " + exception.getMessage());
            }
        }
        yaml.save(file);
    }

    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning("[Honeycomb] " + message);
        }
    }

    /**
     * @return the override file
     */
    public File file() {
        return file;
    }
}
