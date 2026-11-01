package dev.bluelabs.honeycomb.preset;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

/**
 * Ready made tablist and scoreboard layouts that can be loaded or captured.
 *
 * <p>A preset is a small YAML file in {@code plugins/Honeycomb/presets}. Loading one
 * writes the layout into {@code panel.yml}, so the original files stay untouched and
 * the next load replaces the previous preset cleanly.</p>
 */
public final class PresetService {

    private static final List<String> BUNDLED = List.of("smp", "survival", "donutsmp", "economy",
            "pvp", "lifesteal", "skyblock", "prison", "factions");

    /** Placeholders Honeycomb resolves itself; anything else needs PlaceholderAPI. */
    private static final java.util.Set<String> INTERNAL = java.util.Set.of(
            "player_name", "player_displayname", "player_nickname", "player_prefix", "player_suffix",
            "player_group", "player_world", "player_health", "player_max_health", "player_health_percent",
            "player_food", "player_level", "player_ping", "player_x", "player_y", "player_z",
            "player_biome", "player_gamemode", "player_afk", "player_afk_tag", "player_vanished",
            "player_online_time", "player_playtime_ticks", "player_uuid",
            "server_name", "server_online", "server_max_players", "server_tps", "server_mspt",
            "server_uptime", "server_uptime_clock", "server_time_zone", "server_version",
            "server_bukkit", "server_memory_used", "server_memory_max", "server_memory_percent",
            "network_online", "network_server", "world_players");

    private static final java.util.regex.Pattern EXTERNAL =
            java.util.regex.Pattern.compile("%([a-z0-9_]+)%");
    private static final String TOKEN = "YOURSERVER";
    private static final String DOMAIN = "yourserver.net";

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public PresetService(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Copies the bundled presets into the data folder and refreshes them when a new
     * build ships a different set. A preset the owner edited is backed up before it
     * is replaced, so nothing is lost.
     */
    public void ensureDefaults() {
        Path folder = folder();
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create the presets folder: " + exception.getMessage());
            return;
        }

        String version = plugin.getPluginMeta().getVersion();
        Path stamp = folder.resolve(".bundled");
        String shipped = readStamp(stamp);
        boolean refresh = !version.equals(shipped);

        int replaced = 0;
        int backedUp = 0;
        for (String name : BUNDLED) {
            byte[] bundled = resource("presets/" + name + ".yml");
            if (bundled == null) {
                continue;
            }
            Path target = folder.resolve(name + ".yml");
            try {
                if (!Files.exists(target)) {
                    Files.write(target, bundled);
                    replaced++;
                    continue;
                }
                if (!refresh) {
                    continue;
                }
                byte[] onDisk = Files.readAllBytes(target);
                if (java.util.Arrays.equals(onDisk, bundled)) {
                    continue;
                }
                Path backup = folder.resolve("backup-" + shipped).resolve(name + ".yml");
                Files.createDirectories(backup.getParent());
                Files.write(backup, onDisk);
                backedUp++;
                Files.write(target, bundled);
                replaced++;
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write preset " + name + ": " + exception.getMessage());
            }
        }

        if (refresh) {
            writeStamp(stamp, version);
            if (replaced > 0) {
                plugin.getLogger().info("Preset set updated to " + version + ": " + replaced
                        + " file(s) written" + (backedUp > 0
                        ? ", " + backedUp + " edited copy(ies) kept in presets/backup-" + shipped
                        : "") + ".");
            }
        }
    }

    private byte[] resource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    private String readStamp(Path stamp) {
        try {
            return Files.exists(stamp) ? Files.readString(stamp).trim() : "none";
        } catch (IOException exception) {
            return "none";
        }
    }

    private void writeStamp(Path stamp, String version) {
        try {
            Files.writeString(stamp, version);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write the preset stamp: " + exception.getMessage());
        }
    }

    /**
     * @return the preset ids found on disk
     */
    public List<String> names() {
        File[] files = folder().toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        List<String> names = new ArrayList<>();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            String name = file.getName();
            names.add(name.substring(0, name.length() - 4));
        }
        names.sort(String::compareTo);
        return names;
    }

    /**
     * @param id preset id
     * @return the one line description, or an empty string
     */
    public String description(String id) {
        YamlConfiguration yaml = read(id);
        return yaml == null ? "" : yaml.getString("description", "");
    }

    /**
     * @param id preset id
     * @return the short display name, for example SMP or DonutSMP
     */
    public String label(String id) {
        YamlConfiguration yaml = read(id);
        String name = yaml == null ? null : yaml.getString("name");
        return name == null || name.isBlank() ? id.toUpperCase(Locale.ROOT) : name;
    }

    /**
     * Applies a preset over the current layout.
     *
     * @param id     preset id
     * @param sender who to report to
     * @return true when the preset was applied
     */
    public boolean load(String id, CommandSender sender) {
        YamlConfiguration yaml = read(id);
        if (yaml == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>There is no preset called <white>" + id + "</white>."));
            return false;
        }
        String label = text(yaml, "name", id.toUpperCase(Locale.ROOT));
        int tabLines = 0;
        List<String> header = yaml.getStringList("tablist.header");
        List<String> footer = yaml.getStringList("tablist.footer");
        String listName = yaml.getString("tablist.player-list-name");
        if (!header.isEmpty()) {
            plugin.panelOverrides().set(ConfigType.TABLIST, "tablist.header", substitute(header));
            tabLines += header.size();
        }
        if (!footer.isEmpty()) {
            plugin.panelOverrides().set(ConfigType.TABLIST, "tablist.footer", substitute(footer));
            tabLines += footer.size();
        }
        if (listName != null && !listName.isBlank()) {
            plugin.panelOverrides().set(ConfigType.TABLIST, "tablist.player-list-name", substitute(listName));
        }
        String title = yaml.getString("scoreboard.title");
        List<String> lines = yaml.getStringList("scoreboard.lines");
        if (title != null && !title.isBlank()) {
            plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "default.title", substitute(title));
        }
        if (!lines.isEmpty()) {
            plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "default.lines", substitute(lines));
        }
        try {
            plugin.panelOverrides().save();
        } catch (IOException exception) {
            sender.sendMessage(Component.text("Could not save panel.yml: " + exception.getMessage()));
            return false;
        }
        plugin.reload();
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Loaded preset <white>" + label + "</white><green>."
                        + " <gray>" + tabLines + " tablist lines and " + lines.size()
                        + " scoreboard lines applied."));
        reportExternal(header, footer, lines, sender);
        return true;
    }

    /**
     * Captures the current layout as a preset.
     *
     * @param id     preset id to write
     * @param sender who to report to
     * @return true when the file was written
     */
    public boolean save(String id, CommandSender sender) {
        if (!id.matches("[A-Za-z0-9_\\-]{1,32}")) {
            sender.sendMessage(Component.text("Use letters, digits, dash or underscore for the preset name."));
            return false;
        }
        YamlConfiguration current = plugin.configManager().yaml(ConfigType.TABLIST);
        YamlConfiguration board = plugin.configManager().yaml(ConfigType.SCOREBOARDS);
        YamlConfiguration out = new YamlConfiguration();
        out.set("name", id);
        out.set("description", "Saved from the live layout on " + java.time.LocalDate.now());
        out.set("tablist.header", current.getStringList("tablist.header"));
        out.set("tablist.footer", current.getStringList("tablist.footer"));
        out.set("tablist.player-list-name", current.getString("tablist.player-list-name"));
        out.set("scoreboard.title", board.getString("scoreboards.default.title"));
        out.set("scoreboard.lines", board.getStringList("scoreboards.default.lines"));
        Path target = folder().resolve(id.toLowerCase(Locale.ROOT) + ".yml");
        try {
            Files.createDirectories(folder());
            out.save(target.toFile());
        } catch (IOException exception) {
            sender.sendMessage(Component.text("Could not write " + target.getFileName() + ": "
                    + exception.getMessage()));
            return false;
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Saved the current layout as preset <white>" + id + "</white><green>."
                        + " <gray>File: presets/" + target.getFileName()));
        return true;
    }

    /**
     * Names the external placeholders a preset uses, so nobody wonders why a line
     * shows a raw token when PlaceholderAPI is missing.
     */
    private void reportExternal(List<String> header, List<String> footer, List<String> lines,
                                CommandSender sender) {
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        for (List<String> list : List.of(header, footer, lines)) {
            for (String line : list) {
                if (line == null) {
                    continue;
                }
                var matcher = EXTERNAL.matcher(line);
                while (matcher.find()) {
                    String token = matcher.group(1);
                    if (!INTERNAL.contains(token)) {
                        missing.add("%" + token + "%");
                    }
                }
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        boolean api = false;
        for (var status : plugin.integrations().statuses()) {
            if (status.name().equals("PlaceholderAPI")) {
                api = status.available();
            }
        }
        if (!api) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>This preset reads " + String.join(", ", missing.stream().limit(4).toList())
                            + ". Install PlaceholderAPI and the plugin behind them, or edit those lines"
                            + " in /honeycomb panel."));
        }
    }

    private boolean containsToken(List<String> lines) {
        for (String line : lines) {
            if (line != null && line.contains(TOKEN)) {
                return true;
            }
        }
        return false;
    }

    private List<String> substitute(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(substitute(line));
        }
        return out;
    }

    private String substitute(String line) {
        if (line == null) {
            return "";
        }
        YamlConfiguration config = plugin.configManager().yaml(ConfigType.CONFIG);
        String out = line;
        String serverName = config.getString("server-name", "");
        if (serverName != null && !serverName.isBlank() && !serverName.equalsIgnoreCase(TOKEN)) {
            out = out.replace(TOKEN, serverName);
        }
        String domain = config.getString("server-domain", "");
        if (domain != null && !domain.isBlank() && !domain.equalsIgnoreCase(DOMAIN)) {
            out = out.replace(DOMAIN, domain);
        }
        return out;
    }

    private String text(YamlConfiguration yaml, String path, String fallback) {
        String value = yaml.getString(path);
        return value == null || value.isBlank() ? fallback : value;
    }

    private YamlConfiguration read(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Path file = folder().resolve(id.toLowerCase(Locale.ROOT) + ".yml");
        if (!Files.exists(file)) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    private Path folder() {
        return plugin.getDataFolder().toPath().resolve("presets");
    }
}
