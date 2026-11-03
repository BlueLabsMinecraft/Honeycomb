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
            case "tablist" -> ProfilesPanel.list(plugin, "tablist");
            case "scoreboard", "scoreboards" -> ProfilesPanel.list(plugin, "scoreboards");
            case "nametags" -> ProfilesPanel.list(plugin, "nametags");
            case "bossbars" -> ProfilesPanel.list(plugin, "bossbars");
            case "brand" -> brand();
            case "commands" -> commands();
            case "messages" -> messages();
            case "animations" -> animations();
            case "staff" -> staff();
            case "presets" -> presets();
            case "updates" -> updates();
            case "actionbar", "action-bar" -> ProfilesPanel.list(plugin, "actionbar");
            case "actionbar-entries" -> ProfilesPanel.list(plugin, "actionbar");
            case "network" -> network();
            case "displays" -> displays();
            case "server" -> server();
            case "setup" -> setup();
            case "maintenance" -> maintenance();
            case "layouts", "tablists" -> ProfilesPanel.list(plugin, "tablist");
            case "boards" -> ProfilesPanel.list(plugin, "scoreboards");
            case "bars" -> ProfilesPanel.list(plugin, "bossbars");
            case "groups" -> ProfilesPanel.list(plugin, "nametags");
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
                new Case("tablist", () -> ProfilesPanel.list(plugin, "tablist")),
                new Case("scoreboard", () -> ProfilesPanel.list(plugin, "scoreboards")),
                new Case("nametags", () -> ProfilesPanel.list(plugin, "nametags")),
                new Case("bossbars", () -> ProfilesPanel.list(plugin, "bossbars")),
                new Case("actionbar", () -> ProfilesPanel.list(plugin, "actionbar")),
                new Case("brand", this::brand),
                new Case("commands", this::commands),
                new Case("messages", this::messages),
                new Case("animations", this::animations),
                new Case("staff", this::staff),
                new Case("presets", this::presets),
                new Case("presetsave", this::presetsave),
                new Case("updates", this::updates),
                new Case("displays", this::displays),
                new Case("server", this::server),
                new Case("setup", this::setup),
                new Case("maintenance", this::maintenance));
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
            DialogKit.show(player, plugin, dialog);
        } catch (Throwable error) {
            player.sendMessage(Component.text("Honeycomb could not open that dialog: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Failed to show panel dialog to " + player.getName() + ": " + error);
        }
    }

    // ---------------------------------------------------------------- sections

    private Dialog main() {
        YamlConfiguration config = plugin.configManager().yaml(ConfigType.CONFIG);
        boolean brand = config.getBoolean("brand.enabled", false);
        boolean hider = config.getBoolean("commands.hide-pl-version", true);
        List<Component> body = List.of(
                DialogKit.heading("WHAT DO YOU WANT TO CHANGE?"),
                DialogKit.styled("<shadow:#000000:1><gray>Four groups. Pick one, then pick what you want to edit."),
                DialogKit.styled("<shadow:#000000:1><white>Displays <dark_gray>- the five things players see on screen."),
                DialogKit.styled("<shadow:#000000:1><white>Server <dark_gray>- F3 brand, messages, proxy network, staff view."),
                DialogKit.styled("<shadow:#000000:1><white>Setup <dark_gray>- presets, TAB import, animations."),
                DialogKit.styled("<shadow:#000000:1><white>Maintenance <dark_gray>- reload, update log, self check."),
                DialogKit.shade("Right now: F3 brand " + (brand ? "on" : "off") + ", /pl version hiding "
                        + (hider ? "on" : "off") + "."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Displays"), BACK + " displays"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Server"), BACK + " server"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Setup"), BACK + " setup"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Maintenance"), BACK + " maintenance"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Honeycomb panel", body, buttons);
    }

    /** The five on-screen displays, each named with what it actually shows. */
    private Dialog displays() {
        List<Component> body = List.of(
                DialogKit.heading("DISPLAYS"),
                DialogKit.styled("<shadow:#000000:1><white>Tablist <dark_gray>- the player list, header and footer."),
                DialogKit.styled("<shadow:#000000:1><white>Sidebar <dark_gray>- the lines down the right of the screen."),
                DialogKit.styled("<shadow:#000000:1><white>Action bar <dark_gray>- the one line just above the hotbar."),
                DialogKit.styled("<shadow:#000000:1><white>Nametags <dark_gray>- prefix and suffix above player heads."),
                DialogKit.styled("<shadow:#000000:1><white>Bossbars <dark_gray>- bars at the top of the screen."),
                DialogKit.shade("The number is how many layouts that display has. Each layout has its own conditions."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(section("Tablist", "tablist"));
        buttons.add(section("Sidebar", "scoreboard"));
        buttons.add(section("Action bar", "actionbar"));
        buttons.add(section("Nametags", "nametags"));
        buttons.add(section("Bossbars", "bossbars"));
        buttons.add(DialogKit.run("Back", "honeycomb panel"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Displays", body, buttons);
    }

    /** Server wide behaviour: brand line, messages, proxy network, staff view. */
    private Dialog server() {
        var yaml = plugin.configManager().yaml(ConfigType.NETWORK);
        String state = !yaml.getBoolean("network.enabled", false)
                ? "off"
                : (plugin.network().available() ? "connected to " + plugin.network().proxyName() : "waiting for a proxy");
        List<Component> body = List.of(
                DialogKit.heading("SERVER"),
                DialogKit.styled("<shadow:#000000:1><white>F3 brand <dark_gray>- the text in the debug screen brand line."),
                DialogKit.styled("<shadow:#000000:1><white>Commands <dark_gray>- hide the plugin version from /pl and /plugins."),
                DialogKit.styled("<shadow:#000000:1><white>Messages <dark_gray>- the wording Honeycomb sends to players."),
                DialogKit.styled("<shadow:#000000:1><white>Network <dark_gray>- Proxy counts across your server group."),
                DialogKit.styled("<shadow:#000000:1><white>Staff layouts <dark_gray>- who gets the staff tablist and sidebar."),
                DialogKit.shade("Network is " + state + "."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("F3 brand", "brand"));
        buttons.add(button("Commands", "commands"));
        buttons.add(button("Messages", "messages"));
        buttons.add(button("Network", "network"));
        buttons.add(button("Staff layouts", "staff"));
        buttons.add(DialogKit.run("Back", "honeycomb panel"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Server", body, buttons);
    }

    /** Presets, the TAB import and the animation list. */
    private Dialog setup() {
        List<Component> body = List.of(
                DialogKit.heading("SETUP"),
                DialogKit.styled("<shadow:#000000:1><white>Presets <dark_gray>- nine ready made layouts. Load one, then edit it."),
                DialogKit.styled("<shadow:#000000:1><white>Import from TAB <dark_gray>- copies your TAB header, sidebar and tags."),
                DialogKit.styled("<shadow:#000000:1><white>Animations <dark_gray>- the frame lists you can use as {honeycomb.id}."),
                DialogKit.shade("Importing backs up your current files before it writes anything."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Presets", "presets"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Import from TAB"), "honeycomb migrate"));
        buttons.add(button("Animations", "animations"));
        buttons.add(DialogKit.run("Back", "honeycomb panel"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Setup", body, buttons);
    }

    /** Reload, the update log and a self check of every screen. */
    private Dialog maintenance() {
        List<Component> body = List.of(
                DialogKit.heading("MAINTENANCE"),
                DialogKit.styled("<shadow:#000000:1><white>Apply changes <dark_gray>- reloads every file, no restart needed."),
                DialogKit.styled("<shadow:#000000:1><white>Update log <dark_gray>- the notes players see with /updatelog."),
                DialogKit.styled("<shadow:#000000:1><white>Self check <dark_gray>- builds every panel screen and reports errors."),
                DialogKit.shade("Use Apply changes after editing files by hand."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><bold><green>Apply changes"), "honeycomb reload"));
        buttons.add(button("Update log", "updates"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Self check"), "honeycomb panel debug"));
        buttons.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Command list"), "honeycomb help"));
        buttons.add(DialogKit.run("Back", "honeycomb panel"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Maintenance", body, buttons);
    }

    /** A navigation button that opens a section of this panel. */
    private ActionButton button(String label, String route) {
        return DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>" + label), BACK + " " + route);
    }

    /**
     * Builds one section button with the number of layouts it holds.
     *
     * @param label visible name
     * @param route panel route
     * @return the button
     */
    private ActionButton section(String label, String route) {
        int count = ProfilesPanel.count(plugin, countKind(route));
        return DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>" + label
                + " <dark_gray>(" + count + ")"), BACK + " " + route);
    }

    private static String countKind(String route) {
        return switch (route) {
            case "tablist" -> "tablist";
            case "scoreboard" -> "scoreboards";
            case "actionbar" -> "actionbar";
            case "nametags" -> "nametags";
            default -> "bossbars";
        };
    }

    private Dialog network() {
        var yaml = plugin.configManager().yaml(dev.bluelabs.honeycomb.config.ConfigType.NETWORK);
        boolean on = yaml.getBoolean("network.enabled", false);
        String type = yaml.getString("network.type", "auto");
        var proxy = plugin.network();
        String state = on
                ? (proxy.available()
                    ? "<green>connected <dark_gray>to <white>" + proxy.proxyName()
                    : "<gold>waiting for the proxy")
                : "<dark_gray>off";
        List<Component> body = List.of(
                DialogKit.heading("NETWORK"),
                DialogKit.styled("<shadow:#000000:1><gray>Proxy: <white>" + proxy.proxyName()
                        + " <dark_gray>| <gray>Type: <white>" + type),
                DialogKit.styled("<shadow:#000000:1><gray>State: " + state),
                DialogKit.shade(proxy.available()
                        ? "Online now: " + proxy.onlineEverywhere() + ", servers: " + proxy.servers().size()
                        : "Turn it on for Velocity or BungeeCord counts."));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.act(DialogKit.styled(on
                        ? "<shadow:#000000:1><bold><red>Turn off"
                        : "<shadow:#000000:1><bold><green>Turn on"),
                audience -> toggleNetwork()));
        buttons.add(DialogKit.act(DialogKit.styled("<shadow:#000000:1><white>Type: <yellow>" + type),
                audience -> cycleNetworkType()));
        buttons.add(DialogKit.run("Back", BACK + " main"));
        buttons.add(DialogKit.close());
        return DialogKit.menu("Network", body, buttons);
    }

    private void toggleNetwork() {
        var yaml = plugin.configManager().yaml(dev.bluelabs.honeycomb.config.ConfigType.NETWORK);
        boolean on = yaml.getBoolean("network.enabled", false);
        plugin.panelOverrides().set(dev.bluelabs.honeycomb.config.ConfigType.NETWORK, "network.enabled", !on);
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException error) {
            return;
        }
        plugin.reload();
    }

    private void cycleNetworkType() {
        var yaml = plugin.configManager().yaml(dev.bluelabs.honeycomb.config.ConfigType.NETWORK);
        String current = yaml.getString("network.type", "auto");
        String next = switch (current == null ? "auto" : current.toLowerCase(java.util.Locale.ROOT)) {
            case "auto" -> "bungee";
            case "bungee" -> "velocity";
            default -> "auto";
        };
        plugin.panelOverrides().set(dev.bluelabs.honeycomb.config.ConfigType.NETWORK, "network.type", next);
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException error) {
            return;
        }
        plugin.reload();
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
        body.add(DialogKit.shade("Staff tablist: " + (tab ? "<green>on" : "<red>off")
                + " <dark_gray>| <gray>Staff scoreboard: " + (scoreboard ? "<green>on" : "<red>off")));
        body.add(DialogKit.shade("Staff layouts only show for <white>honeycomb.staff</white>."));
        body.add(DialogKit.shade("With a layout off, staff see the normal one instead of nothing."));
        body.add(DialogKit.hint("Header and footer: one line each, MiniMessage allowed."));
        body.add(DialogKit.hint("Scoreboard lines: one row each, 15 rows maximum."));
        List<DialogInput> inputs = List.of(
                DialogKit.lines("header", "Staff tablist header",
                        join(tablist.getStringList("tablist.profiles.staff.header")), 10),
                DialogKit.lines("footer", "Staff tablist footer",
                        join(tablist.getStringList("tablist.profiles.staff.footer")), 10),
                DialogKit.text("title", "Staff scoreboard title",
                        boards.getString("scoreboards.staff.title",
                                "<gradient:#38BDF8:#2563EB><bold>STAFF VIEW</bold></gradient>")),
                DialogKit.lines("lines", "Staff scoreboard lines",
                        join(boards.getStringList("scoreboards.staff.lines")), 18));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(DialogKit.act(DialogKit.styled("<shadow:#000000:1><gray>Staff tablist <white>"
                        + (tab ? "on" : "off") + " <dark_gray>click to turn " + (tab ? "off" : "on")),
                audience -> toggle("staff.tablist", !tab, audience)));
        buttons.add(DialogKit.act(DialogKit.styled("<shadow:#000000:1><gray>Staff scoreboard <white>"
                        + (scoreboard ? "on" : "off") + " <dark_gray>click to turn " + (scoreboard ? "off" : "on")),
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
                DialogKit.multiline("body", "Body, one line per row", "", 22));
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
                DialogKit.lines("frames", "Frames, one line each", "", 18));
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
        } catch (IOException error) {
            player.sendMessage(Component.text("Could not save " + label + ": " + error.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(label + " saved.", NamedTextColor.GREEN));
        try {
            plugin.reload();
        } catch (RuntimeException error) {
            plugin.getLogger().warning("[Honeycomb] " + label + " saved but reload failed: " + error);
            player.sendMessage(Component.text("Saved, but reload failed: " + error.getMessage(),
                    NamedTextColor.YELLOW));
            return;
        }
        open(player, section);
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
