package dev.bluelabs.honeycomb.updates;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the update log shown to players.
 *
 * <p>Entries live in their own file so the panel can rewrite it without touching the
 * commented display configuration.</p>
 */
public final class UpdateLogService {

    /** One published update. */
    public record Entry(String id, String title, String date, List<String> body) {
    }

    private final HoneycombPlugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean enabled = true;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public UpdateLogService(HoneycombPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "updates.yml");
        createDefault();
        reload();
    }

    private void createDefault() {
        if (file.exists()) {
            return;
        }
        try (var stream = plugin.getResource("updates.yml")) {
            if (stream != null) {
                java.nio.file.Files.copy(stream, file.toPath());
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write updates.yml: " + exception.getMessage());
        }
    }

    /** Re-reads the file. */
    public void reload() {
        entries.clear();
        yaml = YamlConfiguration.loadConfiguration(file);
        enabled = yaml.getBoolean("enabled", true);
        ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            List<String> body = new ArrayList<>(entry.getStringList("body"));
            if (body.isEmpty()) {
                String single = entry.getString("text");
                if (single != null) {
                    body.add(single);
                }
            }
            entries.put(id.toLowerCase(Locale.ROOT), new Entry(id,
                    entry.getString("title", id), entry.getString("date", ""), List.copyOf(body)));
        }
    }

    /** @return true when players are allowed to open the log */
    public boolean enabled() {
        return enabled;
    }

    /** @return every entry, newest first */
    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> b.date().compareTo(a.date()));
        return list;
    }

    /** @param id entry id
     *  @return the entry or null */
    public Entry entry(String id) {
        return entries.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    /**
     * Creates or replaces an entry and writes the file.
     *
     * @param id    entry id shown to players
     * @param title headline
     * @param date  date label, free text
     * @param body  lines
     */
    public void save(String id, String title, String date, List<String> body) {
        String key = id.toLowerCase(Locale.ROOT);
        yaml.set("entries." + key + ".title", title);
        yaml.set("entries." + key + ".date", date);
        yaml.set("entries." + key + ".body", body);
        try {
            yaml.save(file);
            plugin.getLogger().info("Saved update log entry '" + id + "'");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save updates.yml: " + exception.getMessage());
        }
        reload();
    }
}
