package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists every subcommand the sender may use, grouped by what it is for.
 *
 * <p>The plain list was hard to read: twelve lines with no order. Commands are now
 * shown in four groups, and the usage column is padded so the descriptions line up.</p>
 */
public final class HelpSubcommand implements Subcommand {

    /** Which group each subcommand belongs to, in the order the groups are printed. */
    private static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        String displays = "displays";
        String server = "server";
        String moving = "moving";
        String other = "other";
        GROUPS.put("panel", displays);
        GROUPS.put("test", displays);
        GROUPS.put("refresh", displays);
        GROUPS.put("reload", server);
        GROUPS.put("placeholders", server);
        GROUPS.put("animations", server);
        GROUPS.put("preset", server);
        GROUPS.put("integrations", server);
        GROUPS.put("migrate", moving);
        GROUPS.put("version", other);
        GROUPS.put("help", other);
    }

    private static final List<String> ORDER = List.of("displays", "server", "moving", "other");

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public HelpSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("?");
    }

    @Override
    public String permission() {
        return "honeycomb.use";
    }

    @Override
    public String usage() {
        return "help";
    }

    @Override
    public String description() {
        return "Shows this command list";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.messages().component("command.help-header", Map.of(
                "version", plugin.version(),
                "mc", plugin.minecraftRange())));

        Map<String, List<Subcommand>> grouped = new LinkedHashMap<>();
        for (String group : ORDER) {
            grouped.put(group, new ArrayList<>());
        }
        for (Subcommand subcommand : plugin.commandRegistry().command().unique()) {
            if (!subcommand.permission().isEmpty() && !sender.hasPermission(subcommand.permission())) {
                continue;
            }
            String group = GROUPS.getOrDefault(subcommand.name().toLowerCase(Locale.ROOT), "other");
            grouped.get(group).add(subcommand);
        }

        for (Map.Entry<String, List<Subcommand>> entry : grouped.entrySet()) {
            List<Subcommand> members = entry.getValue();
            if (members.isEmpty()) {
                continue;
            }
            sender.sendMessage(plugin.messages().component("command.help-group", Map.of()));
            sender.sendMessage(plugin.messages().component(
                    "command.help-title-" + entry.getKey(), Map.of()));
            for (Subcommand subcommand : members) {
                sender.sendMessage(plugin.messages().component("command.help-line", Map.of(
                        "label", "honeycomb",
                        "usage", pad(subcommand.usage()),
                        "description", subcommand.description())));
            }
        }
    }

    /**
     * Pads the usage so every description starts in the same column.
     *
     * @param usage command usage
     * @return usage padded to 30 characters
     */
    private static String pad(String usage) {
        if (usage == null) {
            return "";
        }
        return usage.length() >= 30 ? usage : usage + " ".repeat(30 - usage.length());
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> names = new ArrayList<>();
        for (Subcommand subcommand : plugin.commandRegistry().command().unique()) {
            names.add(subcommand.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
