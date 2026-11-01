package dev.bluelabs.honeycomb.panel;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The in-game configuration panel.
 *
 * <p>Each section is a dialog built from {@link DialogKit}: a short explanation, the
 * fields the section owns, and buttons that save. Saving never touches the commented
 * defaults; values go to {@code panel.yml} and are merged on top, so an owner can
 * always fall back by deleting a line.</p>
 */
public final class ConfigPanel {

    private static final String BACK = "honeycomb panel";

    private final HoneycombPlugin plugin;

    public ConfigPanel(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens a section, defaulting to the main menu. */
    public void open(Player player, String section) {
        String key = section == null ? "main" : section.toLowerCase();
        Dialog dialog = switch (key) {
            case "tablist" -> tablist();
            case "scoreboard", "scoreboards" -> scoreboard();
            case "nametags" -> nametags();
            case "bossbars" -> bossbars();
            case "brand" -> brand();
            case "commands" -> commands();
            case "messages" -> messages();
            case "animations" -> animations();
            case "staff" -> staff();
            case "presets" -> presets();
            case "updates" -> updates();
            default -> main();
        };
        show(player, dialog);
    }

    /**
     * Builds every dialog once and reports what happened.
     *
     * <p>Useful from the console: it proves the dialog API accepts the shapes Honeycomb
     * builds without needing a client to connect.</p>
     */
    public List<String> selfTest() {
        List<String> results = new ArrayList<>();
        record Case(String name, DialogSupplier supplier) {
        }
        List<Case> cases = List.of(
                new Case("main", this::main),
                new Case("tablist", this::tablist),
                new Case("scoreboard", this::scoreboard),
                new Case("nametags", this::nametags),
                new Case("bossbars", this::bossbars),
                new Case("brand", this::brand),
                new Case("commands", this::commands),
                new Case("messages", this::messages),
                new Case("animations", this::animations),
                new Case("staff", this::staff),
                new Case("presets", this::presets),
                new Case("presetsave", this::presetsave),
                new Case("updates", this::updates));
        for (Case test : cases) {
            try {
                Dialog dialog = test.supplier().get();
                results.add(test.name() + ": OK");
            } catch (Throwable error) {
                results.add(test.name() + ": FAILED - " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
                plugin.getLogger().warning("Dialog '" + test.name() + "' failed to build: " + error);
            }
        }
        return results;
    }

    private interface DialogSupplier {
        Dialog get();
    }

    private void show(Player player, Dialog dialog) {
        try {
            player.showDialog(dialog);
        } catch (Throwable error) {
            player.sendMessage(Component.text("Honeycomb could not open that dialog: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Failed to show panel dialog to " + player.getName() + ": " + error);
        }
    }

    // ---------------------------------------------------------------- sections

    private Dialog main() {
        YamlConfiguration config = plugin.configManager().yaml(ConfigType.CONFIG);
        List<Component> body = new ArrayList<>(List.of(
                Component.text("Edit the displays without touching a YAML file."),
                DialogKit.hint("Changes are written to panel.yml and applied instantly."),
                DialogKit.hint("Brand: " + (config.getBoolean("brand.enabled", false) ? "on" : "off")
                        + "  |  /pl + /version hiding: "
                        + (config.getBoolean("commands.hide-pl-version", true) ? "on" : "off"))));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.run("Tablist", BACK + " tablist"));
        buttons.add(DialogKit.run("Scoreboard", BACK + " scoreboard"));
        buttons.add(DialogKit.run("Nametags", BACK + " nametags"));
        buttons.add(DialogKit.run("Bossbars", BACK + " bossbars"));
        buttons.add(DialogKit.run("F3 brand", BACK + " brand"));
        buttons.add(DialogKit.run("Commands", BACK + " commands"));
        buttons.add(DialogKit.run("Messages", BACK + " messages"));
        buttons.add(DialogKit.run("Animations", BACK + " animations"));
        buttons.add(DialogKit.run("Staff layouts", BACK + " staff"));
        buttons.add(DialogKit.run("Presets", BACK + " presets"));
        buttons.add(DialogKit.run("Update log", BACK + " updates"));
        buttons.add(DialogKit.run("Reload all", "honeycomb reload"));
        buttons.add(DialogKit.close());
        body.add(DialogKit.hint("Click a section. Gray text explains what to type."));
        return DialogKit.menu("Honeycomb", body, buttons);
    }

    private Dialog tablist() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.TABLIST);
        List<String> header = strings(yaml, "tablist.header");
        List<String> footer = strings(yaml, "tablist.footer");
        int interval = yaml.getInt("tablist.update-interval", 40);

        List<DialogInput> inputs = List.of(
                DialogKit.lines("header", "Header, one line per row", join(header), 6),
                DialogKit.lines("footer", "Footer, one line per row", join(footer), 4),
                DialogKit.text("interval", "Update interval in ticks", String.valueOf(interval)));

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", this::saveTablist),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Tablist", List.of(DialogKit.hint("Type MiniMessage, e.g. <gray>Welcome <white>%player_name%"), DialogKit.hint("Each header and footer row is one line.")),
                inputs, buttons);
    }

    private Dialog scoreboard() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.SCOREBOARDS);
        String title = yaml.getString("scoreboards.default.title", "<gradient:#FADE29:#D56B2B>HONEYCOMB</gradient>");
        List<String> lines = strings(yaml, "scoreboards.default.lines");
        int interval = yaml.getInt("scoreboards.default.update-interval", 20);

        List<DialogInput> inputs = List.of(
                DialogKit.text("title", "Title", title),
                DialogKit.lines("lines", "Lines, one per row (blank rows allowed)", join(lines), 12),
                DialogKit.text("interval", "Update interval in ticks", String.valueOf(interval)));

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", this::saveScoreboard),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Scoreboard", List.of(DialogKit.hint("One line per row, MiniMessage allowed."), DialogKit.hint("An empty row leaves a gap. Keep it to 15 rows.")),
                inputs, buttons);
    }

    private Dialog nametags() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.NAMETAGS);
        ConfigurationSection groups = yaml.getConfigurationSection("nametags.groups");
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogKit.toggle("enabled", "Nametags enabled", yaml.getBoolean("nametags.enabled", true)));

        List<String> names = new ArrayList<>();
        if (groups != null) {
            for (String name : groups.getKeys(false)) {
                if (names.size() >= 4) {
                    break;
                }
                names.add(name);
                inputs.add(DialogKit.text("prefix_" + name, "Prefix for " + name,
                        groups.getString(name + ".prefix", "")));
            }
        }

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", (player, response) -> saveNametags(player, response, names)),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Nametags", List.of(DialogKit.hint("Type the prefix as MiniMessage, e.g. <red>[ADMIN]</red> "), DialogKit.hint("The highest priority group a player matches wins.")),
                inputs, buttons);
    }

    private Dialog bossbars() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.BOSSBARS);
        ConfigurationSection root = yaml.getConfigurationSection("bossbars");
        String firstId = null;
        if (root != null) {
            for (String key : root.getKeys(false)) {
                if (root.isConfigurationSection(key) && root.getConfigurationSection(key).isString("title")) {
                    firstId = key;
                    break;
                }
            }
        }
        String title = firstId == null ? ""
                : yaml.getString("bossbars." + firstId + ".title", "");

        List<DialogInput> inputs = List.of(
                DialogKit.toggle("enabled", "Bossbars enabled", yaml.getBoolean("bossbars.enabled", true)),
                DialogKit.text("title", "Title of " + (firstId == null ? "the first bar" : firstId), title),
                DialogKit.text("interval", "Update interval in ticks",
                        String.valueOf(yaml.getInt("bossbars.update-interval", 20))));

        String id = firstId;
        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", (player, response) -> saveBossbars(player, response, id)),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Bossbars", List.of(DialogKit.hint("Edit the first bossbar definition.")),
                inputs, buttons);
    }

    private Dialog brand() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.CONFIG);
        List<DialogInput> inputs = List.of(
                DialogKit.toggle("enabled", "Override the F3 brand", yaml.getBoolean("brand.enabled", false)),
                DialogKit.text("text", "Brand text (shown in F3 and the server list)",
                        yaml.getString("brand.text", "Honeycomb")));

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", this::saveBrand),
                DialogKit.run("Back", BACK),
                DialogKit.close());

        // The hint only exists while ProtocolLib is missing.
        java.util.List<Component> body = new java.util.ArrayList<>();
        body.add(DialogKit.hint("Controls the name the client shows in the F3 debug screen."));
        if (dev.bluelabs.honeycomb.server.BrandService.protocolLibPresent()) {
            body.add(DialogKit.good("ProtocolLib detected - the brand below replaces the server name."));
        } else {
            body.add(DialogKit.warn("ProtocolLib is not installed - the F3 brand cannot change."));
            body.add(DialogKit.warn("Drop ProtocolLib.jar into plugins/ and restart."));
        }
        return DialogKit.form("F3 brand", body, inputs, buttons);
    }

    private Dialog commands() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.CONFIG);
        List<DialogInput> inputs = List.of(
                DialogKit.toggle("hide", "Hide /pl, /plugins and /version from non-staff",
                        yaml.getBoolean("commands.hide-pl-version", true)),
                DialogKit.text("extra", "Extra hidden commands, comma separated",
                        String.join(", ", yaml.getStringList("commands.extra-hidden"))));

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", this::saveCommands),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Commands", List.of(
                        DialogKit.hint("Players without honeycomb.commands.view see a plain unknown-command reply.")),
                inputs, buttons);
    }

    private Dialog messages() {
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.MESSAGES);
        List<String> keys = new ArrayList<>();
        for (String key : yaml.getKeys(true)) {
            if (keys.size() >= 10) {
                break;
            }
            if (!yaml.isConfigurationSection(key) && yaml.get(key) instanceof String) {
                keys.add(key);
            }
        }
        List<DialogInput> inputs = new ArrayList<>();
        for (String key : keys) {
            inputs.add(DialogKit.text(inputKey(key), key, yaml.getString(key, "")));
        }

        List<ActionButton> buttons = List.of(
                DialogKit.save("Save", (player, response) -> saveMessages(player, response, keys)),
                DialogKit.run("Back", BACK),
                DialogKit.close());
        return DialogKit.form("Messages", List.of(
                        DialogKit.hint("First ten text entries of messages.yml."),
                        DialogKit.hint("MiniMessage allowed, e.g. <gold>Welcome</gold>")),
                inputs, buttons);
    }

    private Dialog staff() {
        YamlConfiguration config = plugin.configManager().yaml(ConfigType.CONFIG);
        YamlConfiguration tablist = plugin.configManager().yaml(ConfigType.TABLIST);
        YamlConfiguration boards = plugin.configManager().yaml(ConfigType.SCOREBOARDS);
        boolean tab = config.getBoolean("staff.tablist", false);
        boolean scoreboard = config.getBoolean("staff.scoreboard", false);
        List<Component> body = new ArrayList<>();
        body.add(DialogKit.hint("Staff tablist: " + (tab ? "on" : "off")
                + "   Staff scoreboard: " + (scoreboard ? "on" : "off")));
        body.add(DialogKit.hint("These layouts only appear for players with"));
        body.add(DialogKit.hint("honeycomb.staff, everyone else keeps the normal one."));
        body.add(DialogKit.hint("Header and footer: one line each, MiniMessage allowed."));
        body.add(DialogKit.hint("Scoreboard lines: one row each, 15 rows maximum."));
        List<DialogInput> inputs = List.of(
                DialogKit.lines("header", "Staff tablist header",
                        join(tablist.getStringList("tablist.profiles.staff.header")), 4),
                DialogKit.lines("footer", "Staff tablist footer",
                        join(tablist.getStringList("tablist.profiles.staff.footer")), 4),
                DialogKit.text("title", "Staff scoreboard title",
                        boards.getString("scoreboards.staff.title",
                                "<gradient:#38BDF8:#2563EB><bold>STAFF VIEW</bold></gradient>")),
                DialogKit.lines("lines", "Staff scoreboard lines",
                        join(boards.getStringList("scoreboards.staff.lines")), 8));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.act((tab ? "Staff tablist: ON" : "Staff tablist: OFF"),
                audience -> toggle("staff.tablist", !tab, audience)));
        buttons.add(DialogKit.act((scoreboard ? "Staff scoreboard: ON" : "Staff scoreboard: OFF"),
                audience -> toggle("staff.scoreboard", !scoreboard, audience)));
        buttons.add(DialogKit.save("Save layouts", this::saveStaff));
        buttons.add(DialogKit.run("Back", BACK + " main"));
        buttons.add(DialogKit.close());
        return DialogKit.form("Staff layouts", body, inputs, buttons);
    }

    private void saveStaff(Player player, DialogResponseView response) {
        plugin.panelOverrides().set(ConfigType.TABLIST, "tablist.profiles.staff.header",
                split(response.getText("header")));
        plugin.panelOverrides().set(ConfigType.TABLIST, "tablist.profiles.staff.footer",
                split(response.getText("footer")));
        String title = response.getText("title");
        if (title != null && !title.isBlank()) {
            plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "scoreboards.staff.title", title);
        }
        plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "scoreboards.staff.lines",
                split(response.getText("lines")));
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException exception) {
            player.sendMessage(Component.text("Could not save panel.yml: " + exception.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        plugin.reload();
        player.sendMessage(Component.text("Staff layouts saved.", NamedTextColor.GREEN));
        open(player, "staff");
    }

    private void toggle(String path, boolean value, Audience audience) {
        plugin.panelOverrides().set(ConfigType.CONFIG, path, value);
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException exception) {
            audience.sendMessage(Component.text("Could not save panel.yml: " + exception.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        plugin.reload();
        audience.sendMessage(Component.text((value ? "Enabled " : "Disabled ") + path, NamedTextColor.GRAY));
        if (audience instanceof Player player) {
            open(player, "staff");
        }
    }

    private Dialog updates() {
        List<Component> body = new ArrayList<>();
        body.add(Component.text("Publish an update and players read it with /updatelog."));
        body.add(DialogKit.hint("Title and body accept MiniMessage, e.g. <gradient:#FADE29:#D56B2B>"));
        body.add(DialogKit.hint("Saving an id that already exists replaces that entry."));
        for (var entry : plugin.updates().entries()) {
            body.add(mini(entry.id() + "  " + entry.date() + "  " + entry.title(), NamedTextColor.GRAY));
        }
        List<DialogInput> inputs = List.of(
                DialogKit.text("id", "Update id, for example 0.4.0", ""),
                DialogKit.text("title", "Headline", ""),
                DialogKit.text("date", "Date label", ""),
                DialogKit.multiline("body", "Body, one line per row", "", 12));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.save("Publish", (player, response) -> publish(player, response)));
        buttons.add(DialogKit.run("Back", BACK + " main"));
        buttons.add(DialogKit.close());
        return DialogKit.form("Update log", body, inputs, buttons);
    }

    private void publish(Player player, DialogResponseView response) {
        String id = response.getText("id");
        if (id == null || id.isBlank()) {
            player.sendMessage(Component.text("An update needs an id.", NamedTextColor.RED));
            return;
        }
        List<String> body = new ArrayList<>();
        String raw = response.getText("body");
        if (raw != null) {
            for (String line : raw.split("\n")) {
                if (!line.isBlank()) {
                    body.add(line);
                }
            }
        }
        plugin.updates().save(id.trim(), response.getText("title"), response.getText("date"), body);
        player.sendMessage(Component.text("Published update " + id.trim(), NamedTextColor.GREEN));
    }

    /**
     * Opens the list of published updates.
     *
     * @param player viewer
     */
    public void openUpdateLog(Player player) {
        List<Component> body = List.of(DialogKit.hint("Click an update to read it."));
        List<ActionButton> buttons = new ArrayList<>();
        for (var entry : plugin.updates().entries()) {
            buttons.add(DialogKit.run(entry.id() + "  " + entry.title(), "updatelog " + entry.id()));
        }
        if (buttons.isEmpty()) {
            body = List.of(Component.text("No updates have been published yet.",
                    NamedTextColor.GRAY));
        }
        buttons.add(DialogKit.close());
        show(player, DialogKit.menu("Update log", body, buttons));
    }

    /**
     * Opens one published update.
     *
     * @param player viewer
     * @param id     entry id
     */
    public void openUpdateEntry(Player player, String id) {
        var entry = plugin.updates().entry(id);
        if (entry == null) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(mini(entry.title(), NamedTextColor.GOLD));
        if (!entry.date().isBlank()) {
            body.add(DialogKit.hint(entry.date()));
        }
        for (String line : entry.body()) {
            body.add(mini(line, NamedTextColor.WHITE));
        }
        show(player, DialogKit.notice(entry.id(), body, DialogKit.run("Back to the list", "updatelog")));
    }

    private Dialog presets() {
        List<Component> body = new ArrayList<>();
        body.add(DialogKit.hint("Load a ready made tablist and scoreboard look."));
        body.add(DialogKit.hint("Loading replaces the current layout."));
        body.add(DialogKit.hint("Lines with %tokens% need PlaceholderAPI."));
        List<ActionButton> buttons = new ArrayList<>();
        for (String name : plugin.presets().names()) {
            buttons.add(DialogKit.run(plugin.presets().label(name),
                    "honeycomb preset load " + name));
        }
        if (buttons.isEmpty()) {
            body.add(DialogKit.warn("No presets found on disk."));
        }
        buttons.add(DialogKit.run("Save current as preset", BACK + " presetsave"));
        buttons.add(DialogKit.run("Back", BACK + " main"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Presets", body, buttons);
    }

    private Dialog animations() {
        List<Component> body = new ArrayList<>();
        YamlConfiguration yaml = plugin.configManager().yaml(ConfigType.ANIMATIONS);
        ConfigurationSection root = yaml.getConfigurationSection("animations");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection animation = root.getConfigurationSection(id);
                if (animation != null) {
                    body.add(Component.text(id + "  " + animation.getStringList("frames").size()
                            + " frame(s), " + animation.getInt("interval", 10) + "t", NamedTextColor.WHITE));
                }
            }
        }
        if (body.isEmpty()) {
            body.add(DialogKit.hint("No animations yet. Fill the fields below to make one."));
        }
        body.add(DialogKit.hint("Use any animation with {honeycomb.<id>} in the tablist, the"));
        body.add(DialogKit.hint("scoreboard, the F3 brand or a boss bar."));
        body.add(DialogKit.hint("Id: short name. Interval: ticks, 20 = one second."));
        body.add(DialogKit.hint("Frames: one MiniMessage line each, they cycle in order."));
        List<DialogInput> inputs = List.of(
                DialogKit.text("id", "Animation id, for example honeycomb", ""),
                DialogKit.text("interval", "Interval in ticks, for example 5", "5"),
                DialogKit.lines("frames", "Frames, one line each", "", 8));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.save("Create or replace", this::saveAnimation));
        buttons.add(DialogKit.run("Back", BACK + " main"));
        buttons.add(DialogKit.close());
        return DialogKit.form("Animations", body, inputs, buttons);
    }

    private Dialog presetsave() {
        List<Component> body = List.of(
                DialogKit.hint("Write the layout you have now into presets/<name>.yml."),
                DialogKit.hint("Name it after the server, for example my-server."));
        return DialogKit.form("Save preset", body,
                List.of(DialogKit.text("name", "Preset name", "")),
                List.of(DialogKit.save("Save", this::savePreset),
                        DialogKit.run("Back", BACK + " presets"),
                        DialogKit.close()));
    }

    private void savePreset(Player player, DialogResponseView response) {
        String name = response.getText("name");
        if (name == null || name.isBlank()) {
            player.sendMessage(Component.text("The preset needs a name.", NamedTextColor.RED));
            return;
        }
        plugin.presets().save(name.trim(), player);
        open(player, "presets");
    }

    private void saveAnimation(Player player, DialogResponseView response) {
        String id = response.getText("id");
        if (id == null || id.isBlank()) {
            player.sendMessage(Component.text("An animation needs an id.", NamedTextColor.RED));
            return;
        }
        id = id.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        if (id.isEmpty()) {
            player.sendMessage(Component.text("Use letters, digits, dash or underscore for the id.",
                    NamedTextColor.RED));
            return;
        }
        List<String> frames = new ArrayList<>();
        String raw = response.getText("frames");
        if (raw != null) {
            for (String line : raw.split("\\n")) {
                if (!line.isBlank()) {
                    frames.add(line);
                }
            }
        }
        if (frames.isEmpty()) {
            player.sendMessage(Component.text("Add at least one frame.", NamedTextColor.RED));
            return;
        }
        int interval = 5;
        try {
            interval = Math.max(1, Math.min(200, Integer.parseInt(response.getText("interval").trim())));
        } catch (RuntimeException ignored) {
            interval = 5;
        }
        plugin.panelOverrides().set(ConfigType.ANIMATIONS, "animations." + id + ".interval", interval);
        plugin.panelOverrides().set(ConfigType.ANIMATIONS, "animations." + id + ".frames", frames);
        plugin.panelOverrides().set(ConfigType.ANIMATIONS, "animations." + id + ".loop", true);
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException exception) {
            player.sendMessage(Component.text("Could not save panel.yml: " + exception.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        plugin.reload();
        player.sendMessage(Component.text("Animation " + id + " saved. Use {honeycomb." + id + "}",
                NamedTextColor.GREEN));
        open(player, "animations");
    }

    private void saveTablist(Player player, DialogResponseView response) {
        plugin.panelOverrides().set(ConfigType.TABLIST, "header", split(response.getText("header")));
        plugin.panelOverrides().set(ConfigType.TABLIST, "footer", split(response.getText("footer")));
        Integer interval = number(response.getText("interval"));
        if (interval != null) {
            plugin.panelOverrides().set(ConfigType.TABLIST, "update-interval", interval);
        }
        commit(player, "Tablist", "tablist");
    }

    private void saveScoreboard(Player player, DialogResponseView response) {
        String title = response.getText("title");
        if (title != null && !title.isBlank()) {
            plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "default.title", title);
        }
        plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "default.lines", split(response.getText("lines")));
        Integer interval = number(response.getText("interval"));
        if (interval != null) {
            plugin.panelOverrides().set(ConfigType.SCOREBOARDS, "default.update-interval", interval);
        }
        commit(player, "Scoreboard", "scoreboard");
    }

    private void saveNametags(Player player, DialogResponseView response, List<String> groups) {
        plugin.panelOverrides().set(ConfigType.NAMETAGS, "enabled", response.getBoolean("enabled"));
        for (String group : groups) {
            String prefix = response.getText("prefix_" + group);
            if (prefix != null) {
                plugin.panelOverrides().set(ConfigType.NAMETAGS, "groups." + group + ".prefix", prefix);
            }
        }
        commit(player, "Nametags", "nametags");
    }

    private void saveBossbars(Player player, DialogResponseView response, String id) {
        plugin.panelOverrides().set(ConfigType.BOSSBARS, "enabled", response.getBoolean("enabled"));
        String title = response.getText("title");
        if (id != null && title != null && !title.isBlank()) {
            plugin.panelOverrides().set(ConfigType.BOSSBARS, id + ".title", title);
        }
        Integer interval = number(response.getText("interval"));
        if (interval != null) {
            plugin.panelOverrides().set(ConfigType.BOSSBARS, "update-interval", interval);
        }
        commit(player, "Bossbars", "bossbars");
    }

    private void saveBrand(Player player, DialogResponseView response) {
        plugin.panelOverrides().set(ConfigType.CONFIG, "brand.enabled", response.getBoolean("enabled"));
        String text = response.getText("text");
        if (text != null && !text.isBlank()) {
            plugin.panelOverrides().set(ConfigType.CONFIG, "brand.text", text);
        }
        commit(player, "F3 brand", "brand");
    }

    private void saveCommands(Player player, DialogResponseView response) {
        plugin.panelOverrides().set(ConfigType.CONFIG, "commands.hide-pl-version", response.getBoolean("hide"));
        String extra = response.getText("extra");
        if (extra != null) {
            plugin.panelOverrides().set(ConfigType.CONFIG, "commands.extra-hidden",
                    Arrays.stream(extra.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
        commit(player, "Commands", "commands");
    }

    private void saveMessages(Player player, DialogResponseView response, List<String> keys) {
        for (String key : keys) {
            String value = response.getText(inputKey(key));
            if (value != null && !value.isBlank()) {
                plugin.panelOverrides().set(ConfigType.MESSAGES, key, value);
            }
        }
        commit(player, "Messages", "messages");
    }

    private void commit(Player player, String label, String section) {
        try {
            plugin.panelOverrides().save();
            plugin.reload();
            player.sendMessage(Component.text(label + " saved.", NamedTextColor.GREEN));
            open(player, section);
        } catch (IOException error) {
            player.sendMessage(Component.text("Could not save " + label + ": " + error.getMessage(),
                    NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Paper allows only lower case letters, digits and underscores in input names. */
    private static String inputKey(String path) {
        return "msg_" + path.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static List<String> strings(YamlConfiguration yaml, String path) {
        List<String> list = yaml.getStringList(path);
        return list == null ? List.of() : list;
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    private static List<String> split(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static Integer number(String text) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value <= 0 ? null : value;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Placeholder so the type is not flagged unused; kept for readability of saves. */
    static Map<String, Object> empty() {
        return Map.of();
    }

    /** MiniMessage source to a component, with a fallback colour. */
    private static Component mini(String source, NamedTextColor fallback) {
        if (source == null || source.isBlank()) {
            return Component.empty();
        }
        String text = source.indexOf('<') < 0 && source.indexOf('&') < 0
                ? "<color:" + fallback.asHexString() + ">" + source + "</color>"
                : source;
        return MiniMessage.miniMessage().deserialize(text);
    }
}
