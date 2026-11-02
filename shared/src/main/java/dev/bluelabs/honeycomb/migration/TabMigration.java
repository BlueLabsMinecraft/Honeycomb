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

    /** Matches TAB's animation placeholder, which becomes a Honeycomb animation tag. */
    private static final java.util.regex.Pattern ANIMATION =
            java.util.regex.Pattern.compile("(?i)%animation:([A-Za-z0-9_\\-]+)%");

    /** Matches legacy hex colour codes such as {@code &#FF007C}. */
    private static final java.util.regex.Pattern HEX =
            java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})");

    /** Matches one {@code %world%=value} term of a TAB display-condition. */
    private static final java.util.regex.Pattern WORLD_TERM =
            java.util.regex.Pattern.compile("(?i)%world%\\s*=\\s*([^|;]+)");

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
     * Which parts of a TAB install to copy.
     *
     * @param tablist     header and footer designs
     * @param scoreboards sidebar layouts
     * @param belowname   below name text
     * @param nametags    prefixes and suffixes from groups.yml
     * @param bossbars    bossbar definitions
     * @param animations  frames from animations.yml
     * @param override    clear the Honeycomb layouts first instead of merging
     */
    public record Selection(boolean tablist, boolean scoreboards, boolean belowname, boolean nametags,
                            boolean bossbars, boolean animations, boolean override) {

        /** @return every section, merged on top of what is there */
        public static Selection all() {
            return new Selection(true, true, true, true, true, true, false);
        }
    }

    /**
     * Runs the migration with everything selected.
     *
     * @param folder TAB folder
     * @param apply  false for a dry run
     * @return the report
     */
    public Report run(File folder, boolean apply) {
        return run(folder, apply, Selection.all());
    }

    /**
     * Runs the migration.
     *
     * @param folder    TAB folder
     * @param apply     false for a dry run
     * @param selection what to copy and whether to replace
     * @return the report
     */
    public Report run(File folder, boolean apply, Selection selection) {
        List<String> notes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        YamlConfiguration tab = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        File groupsFile = new File(folder, "groups.yml");
        YamlConfiguration groups = groupsFile.isFile()
                ? YamlConfiguration.loadConfiguration(groupsFile)
                : new YamlConfiguration();

        List<String> header = convertList(fetchList(tab, "header-footer.header", "header"));
        List<String> footer = convertList(fetchList(tab, "header-footer.footer", "footer"));

        List<Design> designs = new ArrayList<>();
        ConfigurationSection headerFooter = tab.getConfigurationSection("header-footer");
        ConfigurationSection designRoot = headerFooter == null ? null : headerFooter.getConfigurationSection("designs");
        if (designRoot != null) {
            for (String key : designRoot.getKeys(false)) {
                ConfigurationSection design = designRoot.getConfigurationSection(key);
                if (design == null) {
                    continue;
                }
                List<String> designHeader = convertList(design.getStringList("header"));
                List<String> designFooter = convertList(design.getStringList("footer"));
                if (designHeader.isEmpty() && designFooter.isEmpty()) {
                    continue;
                }
                designs.add(new Design(slug(key), designHeader, designFooter,
                        worlds(design.getString("display-condition", "")), List.of()));
            }
        }
        if (designs.isEmpty() && (!header.isEmpty() || !footer.isEmpty())) {
            designs.add(new Design("default", header, footer, worlds(tab.getString("header-footer.display-condition", "")),
                    List.of()));
        }
        if (!designs.isEmpty()) {
            Design primary = designs.stream().filter(d -> d.id().equals("default")).findFirst().orElse(designs.get(0));
            header = primary.header();
            footer = primary.footer();
        }

        ConfigurationSection scoreboardRoot = tab.getConfigurationSection("scoreboard");
        String title = scoreboardRoot == null ? "" : convert(scoreboardRoot.getString("title", ""));
        List<String> lines = scoreboardRoot == null ? List.of() : convertList(scoreboardRoot.getStringList("lines"));

        List<Design> boards = new ArrayList<>();
        ConfigurationSection boardRoot = scoreboardRoot == null ? null : scoreboardRoot.getConfigurationSection("scoreboards");
        if (boardRoot != null) {
            for (String key : boardRoot.getKeys(false)) {
                ConfigurationSection section = boardRoot.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                List<String> boardLines = convertList(section.getStringList("lines"));
                String boardTitle = convert(section.getString("title", ""));
                if (boardLines.isEmpty() && boardTitle.isBlank()) {
                    continue;
                }
                boards.add(new Design(slug(key), boardLines, List.of(),
                        worlds(section.getString("display-condition", "")),
                        List.of(boardTitle)));
            }
        }
        if (boards.isEmpty() && (!lines.isEmpty() || !title.isBlank())) {
            boards.add(new Design("default", lines, List.of(),
                    worlds(scoreboardRoot == null ? "" : scoreboardRoot.getString("display-condition", "")),
                    List.of(title)));
        }
        if (!boards.isEmpty() && lines.isEmpty()) {
            Design primary = boards.stream().filter(b -> b.id().equals("default")).findFirst().orElse(boards.get(0));
            lines = primary.header();
            title = primary.extra().isEmpty() ? "" : primary.extra().get(0);
        }

        List<Animation> animations = readAnimations(folder, notes, warnings);

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
        ConfigurationSection belowObjective = tab.getConfigurationSection("belowname-objective");
        String belowValue = "";
        boolean belowname = false;
        if (belowObjective != null && belowObjective.getBoolean("enabled", false)) {
            belowname = true;
            belowValue = convert(belowObjective.getString("fancy-value",
                    belowObjective.getString("fancy-value-default", "")));
        } else if (below != null && below.getBoolean("enabled", false)) {
            belowname = true;
        }

        if (tab.isConfigurationSection("layout") && tab.getConfigurationSection("layout").getBoolean("enabled", false)) {
            warnings.add("TAB's layout section (player list grid) has no Honeycomb equivalent, skipped");
        }
        if (tab.isConfigurationSection("playerlist-objective")
                && tab.getConfigurationSection("playerlist-objective").getBoolean("enabled", false)) {
            warnings.add("TAB's playerlist objective column is not migrated, sorting and nametags are");
        }

        if (header.isEmpty() && footer.isEmpty()) {
            warnings.add("No header or footer found in TAB config.yml");
        }
        if (lines.isEmpty() && boards.isEmpty()) {
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
            if (selection.override()) {
                clear(selection);
            }
            if (selection.tablist()) {
                writeTablist(designs.isEmpty()
                        ? List.of(new Design("default", header, footer, List.of(), List.of()))
                        : designs, !tagGroups.isEmpty());
            }
            if (selection.scoreboards()) {
                writeScoreboards(boards.isEmpty()
                        ? List.of(new Design("default", lines, List.of(), List.of(), List.of(title)))
                        : boards);
            }
            if (selection.animations()) {
                writeAnimations(animations);
            }
            if (selection.nametags()) {
                writeGroups(tagGroups);
            }
            if (selection.bossbars()) {
                writeBars(bars);
            }
            if (selection.belowname()) {
                writeBelowName(belowname, belowValue);
            }
            plugin.reload();
        }

        int sidebarLines = boards.stream().mapToInt(b -> b.header().size()).sum();
        notes.add(header.size() + " header line(s)");
        notes.add(footer.size() + " footer line(s)");
        notes.add(Math.max(lines.size(), sidebarLines) + " sidebar line(s)");
        if (designs.size() > 1) {
            notes.add(designs.size() + " tablist design(s) with world conditions");
        }
        if (boards.size() > 1) {
            notes.add(boards.size() + " scoreboard(s) with world conditions");
        }
        notes.add(animations.size() + " animation(s)");
        notes.add(tagGroups.size() + " nametag group(s)");
        notes.add(bars.size() + " bossbar(s)");
        return new Report(apply, header.size(), footer.size(), Math.max(lines.size(), sidebarLines),
                tagGroups.size(), bars.size(), belowname, backup, notes, warnings);
    }

    /**
     * Empties the sections the owner chose to replace, so a TAB design that no
     * longer exists cannot linger in the Honeycomb files.
     *
     * @param selection what the owner selected
     */
    private void clear(Selection selection) {
        if (selection.tablist()) {
            clear(ConfigType.TABLIST, "tablist.profiles", "tablist.header", "tablist.footer",
                    "tablist.player-list-name");
        }
        if (selection.scoreboards()) {
            clear(ConfigType.SCOREBOARDS, "scoreboards");
            var scoreboardFile = plugin.configManager().file(ConfigType.SCOREBOARDS);
            scoreboardFile.yaml().set("scoreboards.enabled", true);
            scoreboardFile.save(plugin.configManager().report());
        }
        if (selection.animations()) {
            clear(ConfigType.ANIMATIONS, "animations");
        }
        if (selection.belowname()) {
            clear(ConfigType.BELOWNAME, "belowname.profiles", "belowname.value");
        }
        if (selection.bossbars()) {
            clear(ConfigType.BOSSBARS, "bossbars.server_info_bar", "bossbars.bossbars");
        }
        if (selection.nametags()) {
            clear(ConfigType.NAMETAGS, "nametags.groups");
        }
    }

    private void clear(ConfigType type, String... paths) {
        var file = plugin.configManager().file(type);
        for (String path : paths) {
            file.yaml().set(path, null);
        }
        file.save(plugin.configManager().report());
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

    private void writeTablist(List<Design> designs, boolean tagGroups) {
        var file = plugin.configManager().file(ConfigType.TABLIST);
        var yaml = file.yaml();
        yaml.set("tablist.enabled", true);
        Design primary = designs.stream().filter(d -> d.id().equals("default")).findFirst().orElse(designs.get(0));
        if (!primary.header().isEmpty()) {
            yaml.set("tablist.header", primary.header());
        }
        if (!primary.footer().isEmpty()) {
            yaml.set("tablist.footer", primary.footer());
        }
        if (tagGroups && yaml.getString("tablist.player-list-name", "").isBlank()) {
            yaml.set("tablist.player-list-name", "<white>%player_name%");
        }
        int priority = 10;
        for (Design design : designs) {
            if (design == primary && design.worlds().isEmpty()) {
                continue;
            }
            String path = "tablist.profiles." + design.id();
            yaml.set(path + ".priority", priority);
            priority += 10;
            if (!design.header().isEmpty()) {
                yaml.set(path + ".header", design.header());
            }
            if (!design.footer().isEmpty()) {
                yaml.set(path + ".footer", design.footer());
            }
            if (!design.worlds().isEmpty()) {
                yaml.set(path + ".conditions.worlds", design.worlds());
            }
        }
        file.save(plugin.configManager().report());
    }

    private void writeScoreboards(List<Design> boards) {
        var file = plugin.configManager().file(ConfigType.SCOREBOARDS);
        var yaml = file.yaml();
        yaml.set("scoreboards.enabled", true);
        int priority = 20;
        for (Design board : boards) {
            String path = "scoreboards." + board.id();
            yaml.set(path + ".enabled", true);
            yaml.set(path + ".priority", priority);
            priority += 10;
            yaml.set(path + ".update-interval", 20);
            yaml.set(path + ".hide-numbers", true);
            String boardTitle = board.extra().isEmpty() ? "" : board.extra().get(0);
            if (!boardTitle.isBlank()) {
                yaml.set(path + ".title", boardTitle);
            }
            if (!board.header().isEmpty()) {
                yaml.set(path + ".lines", board.header());
            }
            if (!board.worlds().isEmpty()) {
                yaml.set(path + ".conditions.worlds", board.worlds());
            }
        }
        file.save(plugin.configManager().report());
    }

    private void writeAnimations(List<Animation> animations) {
        if (animations.isEmpty()) {
            return;
        }
        var file = plugin.configManager().file(ConfigType.ANIMATIONS);
        var yaml = file.yaml();
        for (Animation animation : animations) {
            String path = "animations." + animation.id();
            yaml.set(path + ".interval", animation.interval());
            yaml.set(path + ".loop", true);
            yaml.set(path + ".frames", animation.frames());
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
        if (!bars.containsKey("welcome")) {
            yaml.set("bossbars.welcome.enabled", false);
        }
        bars.forEach((name, values) -> values.forEach((key, value) ->
                yaml.set("bossbars." + name + "." + key, value)));
        file.save(plugin.configManager().report());
    }

    private void writeBelowName(boolean enabled, String value) {
        if (!enabled) {
            return;
        }
        var file = plugin.configManager().file(ConfigType.BELOWNAME);
        var yaml = file.yaml();
        yaml.set("belowname.enabled", true);
        if (value.isBlank()) {
            yaml.set("belowname.mode", "health");
        } else {
            yaml.set("belowname.mode", "custom");
            yaml.set("belowname.value", value);
        }
        file.save(plugin.configManager().report());
    }

    /**
     * Reads TAB's animations.yml. TAB offers several animation types; frame lists
     * are imported, everything else is reported and skipped.
     *
     * @param folder   TAB folder
     * @param notes    notes to add to
     * @param warnings warnings to add to
     * @return animations in TAB order
     */
    private List<Animation> readAnimations(File folder, List<String> notes, List<String> warnings) {
        List<Animation> result = new ArrayList<>();
        File file = new File(folder, "animations.yml");
        if (!file.isFile()) {
            return result;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("animations");
        if (root == null) {
            root = yaml;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            List<String> frames = new ArrayList<>();
            for (String path : List.of("texts", "frames", "text", "lines", "values")) {
                frames.addAll(fetchList(section, path));
            }
            if (frames.isEmpty()) {
                warnings.add("TAB animation '" + key + "' has no text frames, skipped");
                continue;
            }
            int millis = section.getInt("change-interval", section.getInt("interval", 1000));
            int ticks = Math.max(1, millis / 50);
            result.add(new Animation(slug(key), ticks, convertList(frames)));
        }
        if (!result.isEmpty()) {
            notes.add("animations.yml read");
        }
        return result;
    }

    private List<String> fetchList(ConfigurationSection config, String... paths) {
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
            output.add(finish(convert(line)));
        }
        return output;
    }

    /**
     * Handles the parts of TAB text that survive the colour conversion: animation
     * placeholders and hex colour codes.
     *
     * @param input converted line
     * @return line ready for a Honeycomb config file
     */
    static String finish(String input) {
        String text = HEX.matcher(input).replaceAll("<#$1>");
        java.util.regex.Matcher animation = ANIMATION.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (animation.find()) {
            animation.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(
                    "<anim:" + slug(animation.group(1)) + ">"));
        }
        animation.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * @param text design or animation name
     * @return a Honeycomb safe id
     */
    static String slug(String text) {
        String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-").replaceAll("-{2,}", "-");
        id = id.replaceAll("^-|-$", "");
        return id.isEmpty() ? "default" : id;
    }

    /**
     * Reads the worlds out of a TAB display-condition such as
     * {@code %world%=spawn|%world%=arenas}.
     *
     * @param condition TAB condition
     * @return world names, empty when the condition has no world term
     */
    static List<String> worlds(String condition) {
        if (condition == null || condition.isBlank() || condition.equals("''")) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        java.util.regex.Matcher matcher = WORLD_TERM.matcher(condition);
        while (matcher.find()) {
            String world = matcher.group(1).trim();
            if (!world.isEmpty() && !world.contains("%") && !found.contains(world)) {
                found.add(world);
            }
        }
        return found;
    }

    /** One tablist design or scoreboard read from TAB. */
    private record Design(String id, List<String> header, List<String> footer, List<String> worlds,
                          List<String> extra) {
    }

    /** One animation read from TAB's animations.yml. */
    private record Animation(String id, int interval, List<String> frames) {
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
