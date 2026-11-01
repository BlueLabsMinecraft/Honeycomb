package dev.bluelabs.honeycomb.migration;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a TAB installation and writes the same content into the Honeycomb
 * configuration files.
 */
public final class TabMigration {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("0", "<black>"), Map.entry("1", "<dark_blue>"), Map.entry("2", "<dark_green>"),
            Map.entry("3", "<dark_aqua>"), Map.entry("4", "<dark_red>"), Map.entry("5", "<dark_purple>"),
            Map.entry("6", "<gold>"), Map.entry("7", "<gray>"), Map.entry("8", "<dark_gray>"),
            Map.entry("9", "<blue>"), Map.entry("a", "<green>"), Map.entry("b", "<aqua>"),
            Map.entry("c", "<red>"), Map.entry("d", "<light_purple>"), Map.entry("e", "<yellow>"),
            Map.entry("f", "<white>"));

    private static final Map<String, String> FORMATS = Map.of(
            "k", "<obfuscated>", "l", "<bold>", "m", "<strikethrough>",
            "n", "<underlined>", "o", "<italic>", "r", "<reset>");

    private static final Map<String, String> PLACEHOLDERS = Map.ofEntries(
            Map.entry("player", "player_name"),
            Map.entry("displayname", "player_displayname"),
            Map.entry("online", "server_online"),
            Map.entry("ping", "player_ping"),
            Map.entry("health", "player_health"),
            Map.entry("world", "world"),
            Map.entry("server", "server_name"),
            Map.entry("staffonline", "staff_online"),
            Map.entry("group", "luckperms_primary_group_name"),
            Map.entry("servername", "server_name"));

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public TabMigration(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Result of a migration run.
     *
     * @param applied     true when the files were written
     * @param header      number of converted header lines
     * @param footer      number of converted footer lines
     * @param sidebar     number of converted sidebar lines
     * @param groups      number of converted nametag groups
     * @param bars        number of converted bossbars
     * @param belowname   true when the below name display was switched on
     * @param backup      backup folder, null on a dry run
     * @param notes       successful conversions
     * @param warnings    things that were skipped
     */
    public record Report(boolean applied, int header, int footer, int sidebar, int groups, int bars,
                         boolean belowname, File backup, List<String> notes, List<String> warnings) {

        /**
         * @return true when there was anything to convert
         */
        public boolean changed() {
            return header + footer + sidebar + groups + bars > 0 || belowname;
        }
    }

    /**
     * Detects the TAB folder next to the Honeycomb folder.
     *
     * @return the folder, or {@code null} when TAB is not installed
     */
    public File detect() {
        File plugins = plugin.getDataFolder().getParentFile();
        if (plugins == null) {
            return null;
        }
        for (String candidate : List.of("TAB", "tab", "Tab")) {
            File folder = new File(plugins, candidate);
            if (new File(folder, "config.yml").isFile()) {
                return folder;
            }
        }
        return null;
    }

    /**
     * Runs the migration.
     *
     * @param folder TAB folder
     * @param apply  false for a dry run
     * @return the report
     */
    public Report run(File folder, boolean apply) {
        List<String> notes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        YamlConfiguration tab = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        File groupsFile = new File(folder, "groups.yml");
        YamlConfiguration groups = groupsFile.isFile()
                ? YamlConfiguration.loadConfiguration(groupsFile)
                : new YamlConfiguration();

        List<String> header = convertList(fetchList(tab, "header-footer.header", "header"));
        List<String> footer = convertList(fetchList(tab, "header-footer.footer", "footer"));

        ConfigurationSection board = tab.getConfigurationSection("scoreboard");
        String title = board == null ? "" : convert(board.getString("title", ""));
        List<String> lines = board == null ? List.of() : convertList(board.getStringList("lines"));

        Map<String, Map<String, Object>> tagGroups = new LinkedHashMap<>();
        int index = 0;
        for (String key : groups.getKeys(false)) {
            if (!groups.isConfigurationSection(key) || key.startsWith("_")) {
                continue;
            }
            ConfigurationSection group = groups.getConfigurationSection(key);
            if (group == null) {
                continue;
            }
            String prefix = convert(group.getString("tabprefix", group.getString("tagprefix", "")));
            String suffix = convert(group.getString("tagsuffix", ""));
            if (prefix.isBlank() && suffix.isBlank()) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("priority", 100 - index++);
            values.put("prefix", prefix);
            values.put("suffix", suffix);
            tagGroups.put(key.toLowerCase(Locale.ROOT), values);
        }

        ConfigurationSection barRoot = tab.getConfigurationSection("bossbar");
        Map<String, Map<String, Object>> bars = new LinkedHashMap<>();
        if (barRoot != null) {
            ConfigurationSection barSection = barRoot.getConfigurationSection("bars");
            if (barSection != null) {
                int priority = 20;
                for (String key : barSection.getKeys(false)) {
                    ConfigurationSection bar = barSection.getConfigurationSection(key);
                    if (bar == null) {
                        continue;
                    }
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("enabled", true);
                    values.put("priority", priority);
                    priority += 10;
                    values.put("title", convert(bar.getString("text", bar.getString("title", ""))));
                    values.put("color", normalizeColor(bar.getString("color", "YELLOW")));
                    values.put("style", normalizeStyle(bar.getString("style", "SOLID")));
                    values.put("progress", normalizeProgress(bar.get("progress")));
                    bars.put(key.toLowerCase(Locale.ROOT), values);
                }
            }
        }

        ConfigurationSection below = tab.getConfigurationSection("belowname");
        boolean belowname = below != null && below.getBoolean("enabled", false);

        if (header.isEmpty() && footer.isEmpty()) {
            warnings.add("No header or footer found in TAB config.yml");
        }
        if (board == null || lines.isEmpty()) {
            warnings.add("No sidebar lines found in TAB config.yml");
        }
        if (tagGroups.isEmpty()) {
            warnings.add("No tabprefix or tagsuffix found in TAB groups.yml");
        }
        if (bars.isEmpty()) {
            warnings.add("No bossbars found in TAB config.yml");
        }

        File backup = null;
        if (apply) {
            backup = backup();
            writeTablist(header, footer, !tagGroups.isEmpty());
            writeScoreboard(title, lines);
            writeGroups(tagGroups);
            writeBars(bars);
            writeBelowName(belowname);
            plugin.reload();
        }

        notes.add(header.size() + " header line(s)");
        notes.add(footer.size() + " footer line(s)");
        notes.add(lines.size() + " sidebar line(s)");
        notes.add(tagGroups.size() + " nametag group(s)");
        notes.add(bars.size() + " bossbar(s)");
        return new Report(apply, header.size(), footer.size(), lines.size(), tagGroups.size(), bars.size(),
                belowname, backup, notes, warnings);
    }

    private File backup() {
        File root = new File(plugin.getDataFolder(), "migration-backup");
        File target = new File(root, LocalDateTime.now().format(STAMP));
        if (!target.mkdirs()) {
            plugin.getLogger().warning("Could not create migration backup folder");
            return null;
        }
        for (ConfigType type : List.of(ConfigType.TABLIST, ConfigType.SCOREBOARDS, ConfigType.NAMETAGS,
                ConfigType.BOSSBARS, ConfigType.BELOWNAME)) {
            File file = plugin.configManager().file(type).file();
            if (!file.isFile()) {
                continue;
            }
            try {
                Path destination = new File(target, type.fileName()).toPath();
                Files.copy(file.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not back up " + type.fileName() + ": " + exception.getMessage());
            }
        }
        return target;
    }

    private void writeTablist(List<String> header, List<String> footer, boolean tagGroups) {
        var file = plugin.configManager().file(ConfigType.TABLIST);
        var yaml = file.yaml();
        yaml.set("tablist.enabled", true);
        if (!header.isEmpty()) {
            yaml.set("tablist.header", header);
        }
        if (!footer.isEmpty()) {
            yaml.set("tablist.footer", footer);
        }
        if (tagGroups && yaml.getString("tablist.player-list-name", "").isBlank()) {
            yaml.set("tablist.player-list-name", "<white>%player_name%");
        }
        file.save(plugin.configManager().report());
    }

    private void writeScoreboard(String title, List<String> lines) {
        var file = plugin.configManager().file(ConfigType.SCOREBOARDS);
        var yaml = file.yaml();
        yaml.set("scoreboards.enabled", true);
        yaml.set("scoreboards.default.priority", 0);
        yaml.set("scoreboards.default.update-interval", 20);
        yaml.set("scoreboards.default.hide-numbers", true);
        if (!title.isBlank()) {
            yaml.set("scoreboards.default.title", title);
        }
        if (!lines.isEmpty()) {
            yaml.set("scoreboards.default.lines", lines);
        }
        file.save(plugin.configManager().report());
    }

    private void writeGroups(Map<String, Map<String, Object>> groups) {
        if (groups.isEmpty()) {
            return;
        }
        var file = plugin.configManager().file(ConfigType.NAMETAGS);
        var yaml = file.yaml();
        yaml.set("nametags.enabled", true);
        groups.forEach((name, values) -> values.forEach((key, value) ->
                yaml.set("nametags.groups." + name + "." + key, value)));
        file.save(plugin.configManager().report());
    }

    private void writeBars(Map<String, Map<String, Object>> bars) {
        if (bars.isEmpty()) {
            return;
        }
        var file = plugin.configManager().file(ConfigType.BOSSBARS);
        var yaml = file.yaml();
        yaml.set("bossbars.enabled", true);
        bars.forEach((name, values) -> values.forEach((key, value) ->
                yaml.set("bossbars." + name + "." + key, value)));
        file.save(plugin.configManager().report());
    }

    private void writeBelowName(boolean enabled) {
        if (!enabled) {
            return;
        }
        var file = plugin.configManager().file(ConfigType.BELOWNAME);
        var yaml = file.yaml();
        yaml.set("belowname.enabled", true);
        yaml.set("belowname.mode", "health");
        file.save(plugin.configManager().report());
    }

    private List<String> fetchList(YamlConfiguration config, String... paths) {
        for (String path : paths) {
            List<String> found = config.getStringList(path);
            if (!found.isEmpty()) {
                return found;
            }
            String single = config.getString(path);
            if (single != null && !single.isBlank()) {
                return List.of(single);
            }
        }
        return List.of();
    }

    private List<String> convertList(List<String> input) {
        List<String> output = new ArrayList<>(input.size());
        for (String line : input) {
            output.add(convert(line));
        }
        return output;
    }

    /**
     * Converts a TAB line, which uses legacy colour codes and its own built in
     * placeholders, into a MiniMessage line Honeycomb can render.
     *
     * @param input TAB text
     * @return MiniMessage text
     */
    public static String convert(String input) {
        if (input == null) {
            return "";
        }
        String source = trimTrailingCodes(input);
        if (source.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(source.length() + 24);
        List<String> open = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '&' && c != '\u00a7') {
                out.append(c);
                continue;
            }
            if (i + 7 < source.length() && source.charAt(i + 1) == '#') {
                String hex = source.substring(i + 2, i + 8);
                if (hex.matches("[0-9A-Fa-f]{6}")) {
                    closeAll(out, open);
                    out.append("<color:#").append(hex).append('>');
                    open.add("color");
                    i += 7;
                    continue;
                }
            }
            if (i + 1 >= source.length()) {
                out.append(c);
                continue;
            }
            String code = String.valueOf(Character.toLowerCase(source.charAt(i + 1)));
            String colorTag = COLORS.get(code);
            String formatTag = FORMATS.get(code);
            if (colorTag != null) {
                closeAll(out, open);
                out.append(colorTag);
                open.add(colorTag.substring(1, colorTag.length() - 1));
                i++;
                continue;
            }
            if (formatTag != null) {
                String name = formatTag.substring(1, formatTag.length() - 1);
                if ("reset".equals(name)) {
                    closeAll(out, open);
                } else {
                    out.append(formatTag);
                    open.add(name);
                }
                i++;
                continue;
            }
            out.append(c);
        }
        closeAll(out, open);
        String text = out.toString();
        for (Map.Entry<String, String> entry : PLACEHOLDERS.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", "%" + entry.getValue() + "%");
        }
        return text;
    }

    private static void closeAll(StringBuilder out, List<String> open) {
        for (int index = open.size() - 1; index >= 0; index--) {
            out.append("</").append(open.get(index)).append('>');
        }
        open.clear();
    }

    private static String trimTrailingCodes(String input) {
        String value = input;
        while (true) {
            String trimmed = value.stripTrailing();
            if (trimmed.length() >= 8 && trimmed.charAt(trimmed.length() - 8) == '&'
                    && trimmed.charAt(trimmed.length() - 7) == '#'
                    && trimmed.substring(trimmed.length() - 6).matches("[0-9A-Fa-f]{6}")) {
                value = trimmed.substring(0, trimmed.length() - 8);
                continue;
            }
            if (trimmed.length() >= 2) {
                char marker = trimmed.charAt(trimmed.length() - 2);
                String code = String.valueOf(Character.toLowerCase(trimmed.charAt(trimmed.length() - 1)));
                if ((marker == '&' || marker == '\u00a7')
                        && (COLORS.containsKey(code) || FORMATS.containsKey(code))) {
                    value = trimmed.substring(0, trimmed.length() - 2);
                    continue;
                }
            }
            return value;
        }
    }

    private String normalizeColor(String value) {
        String upper = value == null ? "YELLOW" : value.toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (upper) {
            case "PINK" -> "PINK";
            case "BLUE", "GREEN", "RED", "YELLOW", "PURPLE", "WHITE" -> upper;
            default -> "YELLOW";
        };
    }

    private String normalizeStyle(String value) {
        String upper = value == null ? "SOLID" : value.toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (upper) {
            case "SEGMENTED_6", "SEGMENTED_10", "SEGMENTED_12", "SEGMENTED_20", "SOLID" -> upper;
            default -> "SOLID";
        };
    }

    private Object normalizeProgress(Object value) {
        if (value instanceof Number number) {
            double progress = number.doubleValue();
            return progress > 1.0 ? progress / 100.0 : progress;
        }
        return 1.0;
    }
}
