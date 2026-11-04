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
        body.add(DialogKit.shade("Pick a name to edit it. The green button adds a new one."));

        int shown = 0;
        for (String id : ids(plugin, kind)) {
            if (shown++ >= 8) {
                break;
            }
            Component label = DialogKit.styled("<shadow:#000000:1><white>\u25B8 " + id);
            buttons.add(DialogKit.act(label, audience -> {
                if (audience instanceof Player player) {
                    open(player, plugin, kind, id);
                }
            }));
        }
        if (shown == 0) {
            body.add(DialogKit.warn("Nothing here yet. Use the green button below."));
        }

        buttons.add(DialogKit.act(DialogKit.styled("<shadow:#000000:1><bold><green>+ Create new " + noun(kind)),
                audience -> {
            if (audience instanceof Player player) {
                create(player, plugin, kind);
            }
        }));
        buttons.add(DialogKit.run("Back", "honeycomb panel " + screenOf(kind)));
        buttons.add(DialogKit.close());
        return DialogKit.menu(title(kind), body, buttons);
    }

    /**
     * Asks for an id, then opens the editor for the new entry.
     *
     * @param player player
     * @param plugin plugin
     * @param kind   entry kind
     */
    static void create(Player player, HoneycombPlugin plugin, String kind) {
        var dialog = DialogKit.form("New " + noun(kind), List.of(
                        DialogKit.heading("Name the new " + noun(kind)),
                        DialogKit.shade("Lower case, no spaces. Example: nether, staff, pvp.")),
                List.of(DialogKit.text("id", "Name", "")),
                List.of(DialogKit.save("Create", (who, response) -> {
                    String raw = firstText(response, "id", "name");
                    try {
                        String id = create(plugin, kind, raw);
                        if (id == null) {
                            who.sendMessage(plugin.messages().prefixed("panel.name-required",
                                    java.util.Map.of()));
                            return;
                        }
                        if (id.startsWith("=")) {
                            String taken = id.substring(1);
                            who.sendMessage(plugin.messages().prefixed("panel.name-taken",
                                    java.util.Map.of("name", taken)));
                            open(who, plugin, kind, taken);
                            return;
                        }
                        who.sendMessage(plugin.messages().prefixed("panel.created",
                                java.util.Map.of("kind", noun(kind), "name", id)));
                        open(who, plugin, kind, id);
                    } catch (RuntimeException failure) {
                        plugin.getLogger().warning("Creating a " + kind + " from the panel failed: " + failure);
                        who.sendMessage(plugin.messages().prefixed("panel.save-failed",
                                java.util.Map.of("reason", String.valueOf(failure.getMessage()))));
                    }
                }), DialogKit.close()));
        DialogKit.show(player, plugin, dialog);
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
        YamlConfiguration yaml = plugin.configManager().yaml(type(kind));
        String base = base(kind, id);
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogKit.toggle("enabled", "Enabled", yaml.getBoolean(base + ".enabled", true)));

        // Conditions first: they decide who sees the entry, so they belong at the top.
        inputs.add(DialogKit.text("worlds", "Conditions: worlds, comma separated (* and ! work)",
                join(yaml.getStringList(base + ".conditions.worlds"))));
        inputs.add(DialogKit.text("permissions", "Conditions: permissions, comma separated",
                join(yaml.getStringList(base + ".conditions.permissions"))));
        inputs.add(DialogKit.lines("placeholders", "Conditions: placeholder rules, one per row",
                join(yaml.getStringList(base + ".conditions.placeholders")), 5));
        inputs.add(DialogKit.text("priority", "Priority, higher wins",
                String.valueOf(yaml.getInt(base + ".priority", 0))));

        switch (kind) {
            case "tablist" -> {
                inputs.add(DialogKit.lines("header", "Header, one row per line",
                        withIcon(player, "header", join(yaml.getStringList(base + ".header"))), 12));
                inputs.add(DialogKit.lines("footer", "Footer, one row per line",
                        withIcon(player, "footer", join(yaml.getStringList(base + ".footer"))), 8));
                inputs.add(DialogKit.text("list_name", "Player list name",
                        yaml.getString(base + ".player-list-name", "<white>%player_name%")));
            }
            case "scoreboards" -> {
                inputs.add(DialogKit.text("title", "Title", yaml.getString(base + ".title", "")));
                inputs.add(DialogKit.lines("lines", "Lines, one per row",
                        withIcon(player, "lines", join(yaml.getStringList(base + ".lines"))), 18));
                inputs.add(DialogKit.text("interval", "Update interval in ticks",
                        String.valueOf(yaml.getInt(base + ".update-interval", 20))));
                inputs.add(DialogKit.toggle("hide_numbers", "Hide numbers",
                        yaml.getBoolean(base + ".hide-numbers", true)));
            }
            case "bossbars" -> {
                inputs.add(DialogKit.text("title", "Title",
                        withIcon(player, "title", yaml.getString(base + ".title", ""))));
                inputs.add(DialogKit.text("color", "Colour", yaml.getString(base + ".color", "YELLOW")));
                inputs.add(DialogKit.text("style", "Style", yaml.getString(base + ".style", "SOLID")));
                inputs.add(DialogKit.text("progress", "Progress 0 to 1",
                        String.valueOf(yaml.getDouble(base + ".progress", 1.0D))));
            }
            case "actionbar" -> inputs.add(DialogKit.text("template", "Text above the hotbar",
                    withIcon(player, "template", yaml.getString(base + ".template", ""))));
            case "welcome" -> {
                inputs.add(DialogKit.text("scope", "Who sees it: private or broadcast",
                        yaml.getString(base + ".scope", "private")));
                inputs.add(DialogKit.text("trigger", "When: join or leave",
                        yaml.getString(base + ".trigger", "join")));
                inputs.add(DialogKit.lines("lines", "Message, one row per line",
                        withIcon(player, "lines", join(yaml.getStringList(base + ".lines"))), 12));
            }
            default -> {
                inputs.add(DialogKit.text("prefix", "Prefix", yaml.getString(base + ".prefix", "")));
                inputs.add(DialogKit.text("suffix", "Suffix", yaml.getString(base + ".suffix", "")));
            }
        }

        boolean fresh = yaml.getConfigurationSection(base) == null;
        String who = conditionSummary(yaml, base);
        List<Component> body = List.of(
                DialogKit.heading(title(kind).toUpperCase(java.util.Locale.ROOT) + " \u00B7 "
                        + id.toUpperCase(java.util.Locale.ROOT)),
                DialogKit.styled("<shadow:#000000:1><gray>Who sees it: "
                        + (who.startsWith("none") ? "<green>everyone" : "<gold>" + who)),
                DialogKit.shade("Text takes MiniMessage colours. One row per line."),
                DialogKit.shade("Leave a field empty to keep that rule out."));
        List<ActionButton> actions = new ArrayList<>();
        actions.add(DialogKit.save(fresh ? "Create layout" : "Save changes",
                (saved, response) -> save(saved, plugin, kind, id, response)));
        String iconField = iconField(kind);
        actions.add(DialogKit.act(
                DialogKit.styled("<shadow:#000000:1><white>Insert icon"),
                audience -> {
                    if (audience instanceof Player clicker) {
                        IconPicker.open(clicker, plugin, iconField(kind),
                                "honeycomb panel " + screenOf(kind));
                    }
                }));
        actions.add(DialogKit.run("Back", "honeycomb panel " + section(kind)));
        actions.add(DialogKit.close());
        var dialog = DialogKit.form(title(kind) + " - " + id, body, inputs, actions);
        DialogKit.show(player, plugin, dialog);
    }

    /**
     * Writes the dialog values into panel.yml and reloads the plugin.
     *
     * @param player   player saving
     * @param plugin   plugin
     * @param kind     entry kind
     * @param id       entry id
     * @param response dialog values, empty for a new entry
     */
    private static void save(Player player, HoneycombPlugin plugin, String kind, String id,
                             DialogResponseView response) {
        var overrides = plugin.panelOverrides();
        ConfigType type = type(kind);
        String base = base(kind, id);

        overrides.set(type, base + ".enabled", response.getBoolean("enabled"));
        String priority = response.getText("priority");
        if (priority != null && !priority.isBlank()) {
            try {
                overrides.set(type, base + ".priority", Integer.parseInt(priority.trim()));
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text("Priority must be a number, kept the old one.", NamedTextColor.YELLOW));
            }
        }
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
                        overrides.set(type, base + ".update-interval", Integer.parseInt(interval.trim()));
                    } catch (NumberFormatException ignored) {
                        player.sendMessage(Component.text("Interval must be a number.", NamedTextColor.YELLOW));
                    }
                }
            }
            case "bossbars" -> {
                for (String field : List.of("title", "color", "style")) {
                    String value = response.getText(field);
                    if (value != null && !value.isBlank()) {
                        overrides.set(type, base + "." + field, value);
                    }
                }
                String progress = response.getText("progress");
                if (progress != null && !progress.isBlank()) {
                    try {
                        overrides.set(type, base + ".progress", Double.parseDouble(progress.trim()));
                    } catch (NumberFormatException ignored) {
                        player.sendMessage(Component.text("Progress must be a number between 0 and 1.",
                                NamedTextColor.YELLOW));
                    }
                }
            }
            case "welcome" -> {
                String scope = response.getText("scope");
                if (scope != null && !scope.isBlank()) {
                    String clean = scope.trim().toLowerCase(java.util.Locale.ROOT);
                    overrides.set(type, base + ".scope",
                            clean.startsWith("b") ? "broadcast" : "private");
                }
                String trigger = response.getText("trigger");
                if (trigger != null && !trigger.isBlank()) {
                    String clean = trigger.trim().toLowerCase(java.util.Locale.ROOT);
                    overrides.set(type, base + ".trigger", clean.startsWith("l") ? "leave" : "join");
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
                    if (value != null && !value.isBlank()) {
                        overrides.set(type, base + "." + field, value);
                    }
                }
            }
        }

        applyList(overrides, type, base + ".conditions.worlds", response.getText("worlds"));
        applyList(overrides, type, base + ".conditions.permissions", response.getText("permissions"));
        applyList(overrides, type, base + ".conditions.placeholders", response.getText("placeholders"));

        try {
            overrides.save();
        } catch (java.io.IOException error) {
            player.sendMessage(plugin.messages().prefixed("panel.save-failed",
                    java.util.Map.of("reason", String.valueOf(error.getMessage()))));
            return;
        }
        reloadQuietly(plugin);
        player.sendMessage(plugin.messages().prefixed("panel.saved",
                java.util.Map.of("name", id, "kind", noun(kind))));
        // Reopen the editor so the saved values are visible instead of dumping the player
        // back into the world wondering whether it worked.
        open(player, plugin, kind, id);
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

    private static List<String> ids(HoneycombPlugin plugin, String kind) {
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
