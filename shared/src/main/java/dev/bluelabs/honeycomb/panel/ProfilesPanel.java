package dev.bluelabs.honeycomb.panel;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;

/**
 * Lists every tablist design, scoreboard, bossbar and nametag group that exists, and
 * lets an admin edit one or create a new one with its own conditions.
 */
public final class ProfilesPanel {

    private ProfilesPanel() {
    }

    /**
     * Lists the entries of one kind.
     *
     * @param plugin plugin instance
     * @param kind   tablist, scoreboards, bossbars or nametags
     * @return the dialog
     */
    static Dialog list(HoneycombPlugin plugin, String kind) {
        List<ActionButton> buttons = new ArrayList<>();
        List<Component> body = new ArrayList<>();
        List<String> entries = ids(plugin, kind);
        body.add(DialogKit.heading(title(kind).toUpperCase(java.util.Locale.ROOT)));
        body.add(DialogKit.styled("<shadow:#000000:1><gold>" + entries.size() + " "
                + (entries.size() == 1 ? "layout" : "layouts")));
        body.add(DialogKit.shade("Pick a name to edit it, or type a new name below and press Create."));

        int shown = 0;
        for (String id : entries) {
            if (shown++ >= 8) {
                break;
            }
            Component label = DialogKit.styled("<shadow:#000000:1><white>\u25B8 " + id);
            // A command, not a callback: this is the same route the Back button uses and
            // it always lands, whatever the client does with the closing dialog.
            buttons.add(DialogKit.run(label, "honeycomb panel " + screenOf(kind) + "/" + id));
        }
        if (shown == 0) {
            body.add(DialogKit.warn("Nothing here yet. Name one below and press Create."));
        }

        // The name box lives on this screen on purpose. Asking for the name in a second
        // dialog opened from this one meant the click callback had to show a fresh dialog
        // while the client was still closing this one, which left the player looking at an
        // empty world. One screen, one submit, nothing to nest.
        buttons.add(DialogKit.save(DialogKit.styled("<shadow:#000000:1><bold><green>Create"),
                (player, response) -> createHere(player, plugin, kind, response)));
        buttons.add(DialogKit.run("Back", "honeycomb panel " + screenOf(kind)));
        buttons.add(DialogKit.close());
        return DialogKit.form(title(kind), body,
                List.of(DialogKit.text("new_id", "New name, lower case, no spaces (nether, staff, pvp)", "")),
                buttons);
    }

    /**
     * Creates the entry named in the list screen's text box.
     *
     * <p>Runs inside a guarded save handler, so a failure is reported in chat instead of
     * closing the screen silently.</p>
     *
     * @param player   player who pressed Create
     * @param plugin   plugin
     * @param kind     entry kind
     * @param response submitted values
     */
    private static void createHere(Player player, HoneycombPlugin plugin, String kind,
                                   io.papermc.paper.dialog.DialogResponseView response) {
        String raw = firstText(response, "new_id");
        String id = sanitiseId(raw);
        if (id.isEmpty()) {
            player.sendMessage(plugin.messages().prefixed("panel.name-required", java.util.Map.of()));
            return;
        }
        if (ids(plugin, kind).contains(id)) {
            player.sendMessage(plugin.messages().prefixed("panel.name-taken",
                    java.util.Map.of("name", id)));
            open(player, plugin, kind, id);
            return;
        }
        String created = create(plugin, kind, id);
        if (created == null) {
            player.sendMessage(plugin.messages().prefixed("panel.name-required", java.util.Map.of()));
            return;
        }
        player.sendMessage(plugin.messages().prefixed("panel.created",
                java.util.Map.of("kind", noun(kind), "name", id)));
        open(player, plugin, kind, created);
    }

    /**
     * Turns whatever was typed into a usable id.
     *
     * @param raw typed name
     * @return lower case letters, digits, dashes and underscores, or {@code ""}
     */
    static String sanitiseId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
    }

    /**
     * Creates an entry and returns the id it was given.
     *
     * <p>Kept separate from the dialog so the naming rules can be tested without a
     * client: a blank answer, a duplicate and a name full of symbols all have to end
     * somewhere sensible instead of closing the screen with nothing to show.</p>
     *
     * @param plugin plugin instance
     * @param kind   entry kind
     * @param raw    what the admin typed
     * @return the new id, {@code null} when the answer was blank, or {@code =id} when
     *         that name already exists and the editor should open instead
     */
    static String create(HoneycombPlugin plugin, String kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = sanitiseId(raw);
        if (id.isEmpty()) {
            return null;
        }
        if (plugin.configManager().yaml(type(kind)).getConfigurationSection(base(kind, id)) != null) {
            return "=" + id;
        }
        var overrides = plugin.panelOverrides();
        overrides.set(type(kind), base(kind, id) + ".enabled", true);
        try {
            overrides.save();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("could not write panel.yml: " + error.getMessage(), error);
        }
        reloadQuietly(plugin);
        return id;
    }

    /**
     * Reads the first non blank text input out of a dialog response.
     *
     * @param response dialog response
     * @param keys     input names to try, in order
     * @return the value, or {@code null}
     */
    private static String firstText(io.papermc.paper.dialog.DialogResponseView response, String... keys) {
        for (String key : keys) {
            try {
                String value = response.getText(key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (RuntimeException absent) {
                // The key was not part of this dialog, keep looking.
            }
        }
        return null;
    }
    /**
     * Opens the editor for one entry.
     *
     * @param player player
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     */
    static void open(Player player, HoneycombPlugin plugin, String kind, String id) {
        design(player, plugin, kind, id, Draft.empty());
    }

    /**
     * The content screen: only the text players will see.
     *
     * <p>Conditions used to sit on top of the text, which made a simple layout look like a
     * database form. They have their own screen now, so this one answers one question:
     * what does the player see?</p>
     *
     * @param player player
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     * @param draft  typed values to keep after a failed save
     */
    private static void design(Player player, HoneycombPlugin plugin, String kind, String id, Draft draft) {
        DialogKit.show(player, plugin, designDialog(player, plugin, kind, id, draft));
    }

    /**
     * Builds the content screen.
     *
     * @param player player, may be {@code null} when only checking that it builds
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     * @param draft  typed values to keep
     * @return the dialog
     */
    static io.papermc.paper.dialog.Dialog designDialog(Player player, HoneycombPlugin plugin,
                                                       String kind, String id, Draft draft) {
        YamlConfiguration yaml = plugin.configManager().yaml(type(kind));
        String base = base(kind, id);
        List<DialogInput> inputs = new ArrayList<>();

        switch (kind) {
            case "tablist" -> {
                inputs.add(DialogKit.lines("header", "Header - the lines above the player list",
                        draft.text("header", withIcon(player, "header",
                                join(yaml.getStringList(base + ".header")))), 10));
                inputs.add(DialogKit.lines("footer", "Footer - the lines under the player list",
                        draft.text("footer", withIcon(player, "footer",
                                join(yaml.getStringList(base + ".footer")))), 6));
                inputs.add(DialogKit.text("list_name", "Name shown next to each player",
                        draft.text("list_name",
                                yaml.getString(base + ".player-list-name", "<white>%player_name%"))));
            }
            case "scoreboards" -> {
                inputs.add(DialogKit.text("title", "Title - the word at the top of the sidebar",
                        draft.text("title", yaml.getString(base + ".title", ""))));
                inputs.add(DialogKit.lines("lines", "Sidebar lines - one row each, top row first",
                        draft.text("lines", withIcon(player, "lines",
                                join(yaml.getStringList(base + ".lines")))), 16));
                inputs.add(DialogKit.toggle("hide_numbers", "Hide the red score numbers on the right",
                        draft.toggle("hide_numbers", yaml.getBoolean(base + ".hide-numbers", true))));
                inputs.add(DialogKit.text("interval", "Refresh every N ticks (20 ticks = 1 second)",
                        draft.text("interval",
                                String.valueOf(yaml.getInt(base + ".update-interval", 20)))));
            }
            case "bossbars" -> {
                inputs.add(DialogKit.text("title", "Text on the bar",
                        draft.text("title", withIcon(player, "title", yaml.getString(base + ".title", "")))));
                inputs.add(DialogKit.text("color", "Bar colour: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE",
                        draft.text("color", yaml.getString(base + ".color", "YELLOW"))));
                inputs.add(DialogKit.text("style", "Bar style: SOLID, SEGMENTED_6, SEGMENTED_10,"
                        + " SEGMENTED_12 or SEGMENTED_20",
                        draft.text("style", yaml.getString(base + ".style", "SOLID"))));
                inputs.add(DialogKit.text("progress", "How full the bar is: 0.0 empty to 1.0 full",
                        draft.text("progress", String.valueOf(yaml.getDouble(base + ".progress", 1.0D)))));
            }
            case "actionbar" -> inputs.add(DialogKit.text("template", "Text above the hotbar",
                    draft.text("template", withIcon(player, "template",
                            yaml.getString(base + ".template", "")))));
            case "welcome" -> {
                inputs.add(DialogKit.text("trigger", "Send it when a player: JOIN or LEAVE",
                        draft.text("trigger", yaml.getString(base + ".trigger", "join"))));
                inputs.add(DialogKit.text("scope", "Who reads it: PRIVATE to that player, BROADCAST to everyone",
                        draft.text("scope", yaml.getString(base + ".scope", "private"))));
                inputs.add(DialogKit.lines("lines", "Message - one row per line",
                        draft.text("lines", withIcon(player, "lines",
                                join(yaml.getStringList(base + ".lines")))), 10));
            }
            default -> {
                inputs.add(DialogKit.text("prefix", "Text in front of the name, e.g. [VIP] ",
                        draft.text("prefix", yaml.getString(base + ".prefix", ""))));
                inputs.add(DialogKit.text("suffix", "Text after the name, e.g. \u00ABstar\u00BB",
                        draft.text("suffix", yaml.getString(base + ".suffix", ""))));
            }
        }

        boolean fresh = yaml.getConfigurationSection(base) == null;
        List<Component> body = new ArrayList<>();
        body.add(DialogKit.heading(title(kind).toUpperCase(java.util.Locale.ROOT) + " \u00B7 "
                + id.toUpperCase(java.util.Locale.ROOT)));
        if (draft.hasError()) {
            body.add(DialogKit.error(draft.error()));
        }
        body.add(DialogKit.note("Fill in what players should read. Colours work with MiniMessage,"
                + " for example <gold><bold>Hello."));
        body.add(DialogKit.note("Who gets this design is set on the next screen."));

        List<ActionButton> actions = new ArrayList<>();
        actions.add(DialogKit.save(fresh ? "Create" : "Save", (saved, response) ->
                saveContent(saved, plugin, kind, id, response)));
        actions.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Who sees this \u2192"),
                "honeycomb panel " + screenOf(kind) + "/" + id + "/audience"));
        actions.add(DialogKit.act(DialogKit.styled("<shadow:#000000:1><white>Insert icon"), audience -> {
            if (audience instanceof Player clicker) {
                IconPicker.open(clicker, plugin, iconField(kind), "honeycomb panel " + screenOf(kind));
            }
        }));
        actions.add(DialogKit.run("Back", "honeycomb panel " + screenOf(kind)));
        actions.add(DialogKit.close());
        return DialogKit.form(title(kind) + " - " + id, body, inputs, actions);
    }

    /**
     * The audience screen: who the design applies to, in plain words.
     *
     * @param player player
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     * @param draft  typed values to keep after a failed save
     */
    static void audience(Player player, HoneycombPlugin plugin, String kind, String id, Draft draft) {
        DialogKit.show(player, plugin, audienceDialog(player, plugin, kind, id, draft));
    }

    /**
     * Builds the audience screen.
     *
     * @param player player, may be {@code null} when only checking that it builds
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     * @param draft  typed values to keep
     * @return the dialog
     */
    static io.papermc.paper.dialog.Dialog audienceDialog(Player player, HoneycombPlugin plugin,
                                                         String kind, String id, Draft draft) {
        YamlConfiguration yaml = plugin.configManager().yaml(type(kind));
        String base = base(kind, id);
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogKit.toggle("enabled", "Turn this design on",
                draft.toggle("enabled", yaml.getBoolean(base + ".enabled", true))));
        inputs.add(DialogKit.text("worlds", "Worlds - where it is used. Empty means every world",
                draft.text("worlds", join(yaml.getStringList(base + ".conditions.worlds")))));
        inputs.add(DialogKit.text("ranks", "Only for these ranks - permission or LuckPerms group,"
                + " comma separated. Empty means everyone",
                draft.text("ranks", join(yaml.getStringList(base + ".conditions.permissions")))));
        inputs.add(DialogKit.text("priority", "Priority - higher number wins when two designs fit"
                + " the same player. Empty means 0",
                draft.text("priority", String.valueOf(yaml.getInt(base + ".priority", 0)))));
        inputs.add(DialogKit.lines("placeholders", "Advanced: show only when a placeholder matches."
                + " One rule per row, written as  %placeholder% == value",
                draft.text("placeholders", join(yaml.getStringList(base + ".conditions.placeholders"))), 5));

        List<Component> body = new ArrayList<>();
        body.add(DialogKit.heading("WHO SEES THIS"));
        if (draft.hasError()) {
            body.add(DialogKit.error(draft.error()));
        }
        body.add(DialogKit.note("Right now: " + conditionSummary(yaml, base)));
        body.add(DialogKit.note("Leave both boxes empty and every player gets this design,"
                + " which is what most servers want."));
        body.add(DialogKit.note("Worlds: \"*\" means all worlds, \"!nether\" means all except the nether."));

        List<ActionButton> actions = new ArrayList<>();
        actions.add(DialogKit.save("Save", (saved, response) -> saveAudience(saved, plugin, kind, id, response)));
        actions.add(DialogKit.run(DialogKit.styled("<shadow:#000000:1><white>Back to the text"),
                "honeycomb panel " + screenOf(kind) + "/" + id));
        actions.add(DialogKit.close());
        return DialogKit.form(title(kind) + " - " + id + " - who sees it", body, inputs, actions);
    }

    /**
     * Values typed into a screen, kept so a rejected save can reopen the same screen full.
     *
     * @param texts   text field values
     * @param toggles checkbox values
     * @param error   message to show at the top, {@code null} when nothing failed
     */
    record Draft(Map<String, String> texts, Map<String, Boolean> toggles, String error) {

        static Draft empty() {
            return new Draft(Map.of(), Map.of(), null);
        }

        boolean hasError() {
            return error != null && !error.isBlank();
        }

        String text(String key, String fallback) {
            return texts.getOrDefault(key, fallback);
        }

        boolean toggle(String key, boolean fallback) {
            return toggles.getOrDefault(key, fallback);
        }

        /**
         * Reads every listed field back out of a submitted dialog.
         *
         * @param response submitted values
         * @param keys     text fields to keep
         * @param toggles  checkboxes to keep
         * @return the draft
         */
        static Draft capture(DialogResponseView response, List<String> keys, List<String> toggles) {
            Map<String, String> texts = new java.util.HashMap<>();
            Map<String, Boolean> flags = new java.util.HashMap<>();
            for (String key : keys) {
                try {
                    String value = response.getText(key);
                    if (value != null) {
                        texts.put(key, value);
                    }
                } catch (RuntimeException absent) {
                    texts.remove(key);
                }
            }
            for (String key : toggles) {
                try {
                    flags.put(key, response.getBoolean(key));
                } catch (RuntimeException absent) {
                    flags.remove(key);
                }
            }
            return new Draft(texts, flags, null);
        }

        /**
         * @param message what to show at the top of the reopened screen
         * @return a copy carrying the error
         */
        Draft withError(String message) {
            return new Draft(texts, toggles, message);
        }
    }

    /**
     * Writes the content fields and reopens the screen when something does not parse.
     *
     * @param player   player saving
     * @param plugin   plugin
     * @param kind     entry kind
     * @param id       entry id
     * @param response submitted values
     */
    private static void saveContent(Player player, HoneycombPlugin plugin, String kind, String id,
                                    DialogResponseView response) {
        var overrides = plugin.panelOverrides();
        ConfigType type = type(kind);
        String base = base(kind, id);
        List<String> keys = new ArrayList<>(List.of("header", "footer", "list_name", "title", "lines",
                "interval", "color", "style", "progress", "template", "trigger", "scope",
                "prefix", "suffix"));
        Draft draft = Draft.capture(response, keys, List.of("hide_numbers"));

        switch (kind) {
            case "tablist" -> {
                setLines(overrides, type, base + ".header", response.getText("header"));
                setLines(overrides, type, base + ".footer", response.getText("footer"));
                String listName = response.getText("list_name");
                if (listName != null && !listName.isBlank()) {
                    overrides.set(type, base + ".player-list-name", listName);
                }
            }
            case "scoreboards" -> {
                String title = response.getText("title");
                if (title != null && !title.isBlank()) {
                    overrides.set(type, base + ".title", title);
                }
                setLines(overrides, type, base + ".lines", response.getText("lines"));
                overrides.set(type, base + ".hide-numbers", response.getBoolean("hide_numbers"));
                String interval = response.getText("interval");
                if (interval != null && !interval.isBlank()) {
                    try {
                        int ticks = Integer.parseInt(interval.trim());
                        if (ticks < 1 || ticks > 200) {
                            design(player, plugin, kind, id, draft.withError(
                                    "Refreshing must be between 1 and 200 ticks, so 20 for one second."));
                            return;
                        }
                        overrides.set(type, base + ".update-interval", ticks);
                    } catch (NumberFormatException notANumber) {
                        design(player, plugin, kind, id, draft.withError(
                                "Refreshing has to be a whole number of ticks, for example 20."));
                        return;
                    }
                }
            }
            case "bossbars" -> {
                for (String field : List.of("title", "color", "style")) {
                    String value = response.getText(field);
                    if (value != null && !value.isBlank()) {
                        // Colour and style are enum names, so they are stored upper case.
                        overrides.set(type, base + "." + field,
                                field.equals("title") ? value : value.trim().toUpperCase(java.util.Locale.ROOT));
                    }
                }
                String progress = response.getText("progress");
                if (progress != null && !progress.isBlank()) {
                    try {
                        double value = Double.parseDouble(progress.trim());
                        if (value < 0.0D || value > 1.0D) {
                            design(player, plugin, kind, id, draft.withError(
                                    "The bar can only be filled between 0.0 and 1.0, for example 0.5."));
                            return;
                        }
                        overrides.set(type, base + ".progress", value);
                    } catch (NumberFormatException notANumber) {
                        design(player, plugin, kind, id, draft.withError(
                                "How full the bar is has to be a number from 0.0 to 1.0."));
                        return;
                    }
                }
            }
            case "welcome" -> {
                String trigger = response.getText("trigger");
                if (trigger != null && !trigger.isBlank()) {
                    String clean = trigger.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!clean.startsWith("j") && !clean.startsWith("l")) {
                        design(player, plugin, kind, id, draft.withError(
                                "Write JOIN or LEAVE, so Honeycomb knows when to send it."));
                        return;
                    }
                    overrides.set(type, base + ".trigger", clean.startsWith("l") ? "leave" : "join");
                }
                String scope = response.getText("scope");
                if (scope != null && !scope.isBlank()) {
                    String clean = scope.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!clean.startsWith("p") && !clean.startsWith("b")) {
                        design(player, plugin, kind, id, draft.withError(
                                "Write PRIVATE or BROADCAST to choose who reads it."));
                        return;
                    }
                    overrides.set(type, base + ".scope", clean.startsWith("b") ? "broadcast" : "private");
                }
                setLines(overrides, type, base + ".lines", response.getText("lines"));
            }
            case "actionbar" -> {
                String template = response.getText("template");
                if (template != null && !template.isBlank()) {
                    overrides.set(type, base + ".template", template);
                }
            }
            default -> {
                for (String field : List.of("prefix", "suffix")) {
                    String value = response.getText(field);
                    if (value != null) {
                        overrides.set(type, base + "." + field, value);
                    }
                }
            }
        }

        if (!commit(player, plugin, kind, id)) {
            design(player, plugin, kind, id, draft.withError("panel.yml could not be written,"
                    + " so nothing changed. Check the console."));
            return;
        }
        player.sendMessage(plugin.messages().prefixed("panel.saved",
                java.util.Map.of("name", id, "kind", noun(kind))));
        design(player, plugin, kind, id, Draft.empty());
    }

    /**
     * Writes the audience fields.
     *
     * @param player   player saving
     * @param plugin   plugin
     * @param kind     entry kind
     * @param id       entry id
     * @param response submitted values
     */
    private static void saveAudience(Player player, HoneycombPlugin plugin, String kind, String id,
                                     DialogResponseView response) {
        var overrides = plugin.panelOverrides();
        ConfigType type = type(kind);
        String base = base(kind, id);
        Draft draft = Draft.capture(response, List.of("worlds", "ranks", "priority", "placeholders"),
                List.of("enabled"));

        overrides.set(type, base + ".enabled", response.getBoolean("enabled"));
        applyList(overrides, type, base + ".conditions.worlds", response.getText("worlds"));
        applyList(overrides, type, base + ".conditions.permissions", response.getText("ranks"));
        applyList(overrides, type, base + ".conditions.placeholders", response.getText("placeholders"));

        String priority = response.getText("priority");
        if (priority != null && !priority.isBlank()) {
            try {
                overrides.set(type, base + ".priority", Integer.parseInt(priority.trim()));
            } catch (NumberFormatException notANumber) {
                audience(player, plugin, kind, id, draft.withError(
                        "Priority has to be a whole number, for example 10. Leave it empty for 0."));
                return;
            }
        }

        if (!commit(player, plugin, kind, id)) {
            audience(player, plugin, kind, id, draft.withError("panel.yml could not be written,"
                    + " so nothing changed. Check the console."));
            return;
        }
        player.sendMessage(plugin.messages().prefixed("panel.saved",
                java.util.Map.of("name", id, "kind", noun(kind))));
        audience(player, plugin, kind, id, Draft.empty());
    }

    /**
     * Saves the override file and reloads, reporting instead of throwing.
     *
     * @param player player to tell
     * @param plugin plugin
     * @param kind   entry kind
     * @param id     entry id
     * @return true when the write and reload worked
     */
    private static boolean commit(Player player, HoneycombPlugin plugin, String kind, String id) {
        try {
            plugin.panelOverrides().save();
        } catch (java.io.IOException error) {
            plugin.getLogger().warning("Could not write panel.yml: " + error.getMessage());
            return false;
        }
        reloadQuietly(plugin);
        return true;
    }

    /**
     * Reloads after a write, without letting a reload failure swallow the next screen.
     *
     * @param plugin plugin
     */
    private static void reloadQuietly(HoneycombPlugin plugin) {
        try {
            plugin.reload();
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Reload after a panel save failed: " + error);
        }
    }

    /**
     * @param kind entry kind
     * @return the field the icon button feeds, the first text field of that kind
     */
    private static String iconField(String kind) {
        return switch (kind) {
            case "tablist" -> "header";
            case "scoreboards", "welcome" -> "lines";
            case "bossbars" -> "title";
            case "actionbar" -> "template";
            default -> "suffix";
        };
    }

    /** Appends an icon the picker handed back to this field. */
    private static String withIcon(Player player, String field, String current) {
        if (player == null) {
            return current == null ? "" : current;
        }
        String tag = IconPicker.take(player, field);
        if (tag == null) {
            return current;
        }
        String base = current == null ? "" : current;
        return base.isEmpty() ? tag : base + (base.endsWith("\n") ? "" : " ") + tag;
    }

    private static void setLines(dev.bluelabs.honeycomb.config.PanelOverrides overrides, ConfigType type,
                                 String path, String text) {
        if (text == null) {
            return;
        }
        overrides.set(type, path, split(text));
    }

    private static void applyList(dev.bluelabs.honeycomb.config.PanelOverrides overrides, ConfigType type,
                                  String path, String text) {
        if (text == null) {
            return;
        }
        List<String> values = split(text);
        overrides.set(type, path, values.isEmpty() ? null : values);
    }

    private static List<String> split(String text) {
        List<String> values = new ArrayList<>();
        for (String part : text.split("[,\\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    static List<String> ids(HoneycombPlugin plugin, String kind) {
        YamlConfiguration yaml = plugin.configManager().yaml(type(kind));
        ConfigurationSection section = switch (kind) {
            case "tablist" -> yaml.getConfigurationSection("tablist.profiles");
            case "scoreboards" -> yaml.getConfigurationSection("scoreboards");
            case "bossbars" -> yaml.getConfigurationSection("bossbars");
            case "actionbar" -> yaml.getConfigurationSection("actionbar.profiles");
            case "welcome" -> yaml.getConfigurationSection("welcome");
            default -> yaml.getConfigurationSection("nametags.groups");
        };
        List<String> ids = new ArrayList<>();
        if (kind.equals("tablist") || kind.equals("scoreboards")
                || kind.equals("actionbar") || kind.equals("welcome")) {
            ids.add("default");
        }
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (!key.equals("enabled") && !ids.contains(key)) {
                    ids.add(key);
                }
            }
        }
        return ids;
    }

    /**
     * Describes an entry's conditions in a few words.
     *
     * @param yaml config file
     * @param base entry path
     * @return e.g. {@code "none"} or {@code "worlds spawn, arenas"}
     */
    private static String conditionSummary(YamlConfiguration yaml, String base) {
        List<String> parts = new ArrayList<>();
        List<String> worlds = yaml.getStringList(base + ".conditions.worlds");
        List<String> permissions = yaml.getStringList(base + ".conditions.permissions");
        List<String> placeholders = yaml.getStringList(base + ".conditions.placeholders");
        if (!worlds.isEmpty()) {
            parts.add("worlds " + String.join(", ", worlds));
        }
        if (!permissions.isEmpty()) {
            parts.add("permissions " + String.join(", ", permissions));
        }
        if (!placeholders.isEmpty()) {
            parts.add(placeholders.size() + " placeholder rule(s)");
        }
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    static int count(HoneycombPlugin plugin, String kind) {
        return ids(plugin, kind).size();
    }

    static String summary(HoneycombPlugin plugin, String kind, String id) {
        YamlConfiguration yaml = plugin.configManager().yaml(type(kind));
        String base = base(kind, id);
        List<String> parts = new ArrayList<>();
        List<String> worlds = yaml.getStringList(base + ".conditions.worlds");
        List<String> permissions = yaml.getStringList(base + ".conditions.permissions");
        List<String> placeholders = yaml.getStringList(base + ".conditions.placeholders");
        if (!worlds.isEmpty()) {
            parts.add(String.join(",", worlds));
        }
        if (!permissions.isEmpty()) {
            parts.add(String.join(",", permissions));
        }
        if (!placeholders.isEmpty()) {
            parts.add(placeholders.get(0));
        }
        if (parts.isEmpty()) {
            return "everyone";
        }
        return String.join(" ", parts);
    }

    private static String base(String kind, String id) {
        return switch (kind) {
            case "tablist" -> id.equals("default") ? "tablist" : "tablist.profiles." + id;
            case "scoreboards" -> "scoreboards." + id;
            case "bossbars" -> "bossbars." + id;
            case "actionbar" -> id.equals("default") ? "actionbar" : "actionbar.profiles." + id;
            case "welcome" -> id.equals("default") ? "welcome" : "welcome." + id;
            default -> "nametags.groups." + id;
        };
    }

    private static String screenOf(String kind) {
        return switch (kind) {
            case "tablist" -> "tablist";
            case "scoreboards" -> "scoreboard";
            case "bossbars" -> "bossbars";
            case "actionbar" -> "actionbar";
            case "welcome" -> "welcome";
            default -> "nametags";
        };
    }

    static String section(String kind) {
        return switch (kind) {
            case "tablist" -> "layouts";
            case "scoreboards" -> "boards";
            case "bossbars" -> "bars";
            case "actionbar" -> "actionbar-entries";
            case "welcome" -> "welcome";
            default -> "groups";
        };
    }

    static String noun(String kind) {
        return switch (kind) {
            case "tablist" -> "design";
            case "scoreboards" -> "scoreboard";
            case "bossbars" -> "bossbar";
            case "actionbar" -> "text";
            case "welcome" -> "message";
            default -> "group";
        };
    }

    private static String title(String kind) {
        return switch (kind) {
            case "tablist" -> "Tablist designs";
            case "scoreboards" -> "Scoreboards";
            case "bossbars" -> "Bossbars";
            case "actionbar" -> "Action bar texts";
            case "welcome" -> "Welcome and leave messages";
            default -> "Nametag groups";
        };
    }

    private static ConfigType type(String kind) {
        return switch (kind) {
            case "tablist" -> ConfigType.TABLIST;
            case "scoreboards" -> ConfigType.SCOREBOARDS;
            case "bossbars" -> ConfigType.BOSSBARS;
            case "actionbar" -> ConfigType.CONFIG;
            case "welcome" -> ConfigType.WELCOME;
            default -> ConfigType.NAMETAGS;
        };
    }

    private static String join(List<String> values) {
        return String.join("\n", values);
    }

}
