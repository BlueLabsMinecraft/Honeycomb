package dev.bluelabs.honeycomb.command;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.subcommand.AnimationsSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.DebugSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.HelpSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.IntegrationsSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.PanelSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.PlaceholdersSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.PresetSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.RefreshSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.ReloadSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.StatusSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.TestSubcommand;
import dev.bluelabs.honeycomb.command.subcommand.VersionSubcommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Routes {@code /honeycomb} (and its aliases) to a subcommand.
 */
public final class HoneycombCommand implements TabExecutor {

    private final HoneycombPlugin plugin;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    /**
     * @param plugin plugin instance used by the subcommands
     */
    public HoneycombCommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
        List<Subcommand> registered = List.of(
                new HelpSubcommand(plugin),
                new ReloadSubcommand(plugin),
                new VersionSubcommand(plugin),
                new StatusSubcommand(plugin),
                new DebugSubcommand(plugin),
                new TestSubcommand(plugin),
                new AnimationsSubcommand(plugin),
                new PlaceholdersSubcommand(plugin),
                new IntegrationsSubcommand(plugin),
                new RefreshSubcommand(plugin),
                new PresetSubcommand(plugin),
                new PanelSubcommand(plugin)
        );
        for (Subcommand subcommand : registered) {
            subcommands.put(subcommand.name().toLowerCase(Locale.ROOT), subcommand);
            for (String alias : subcommand.aliases()) {
                subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            run(sender, plugin.commandRegistry().help().name(), new String[0]);
            return true;
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            plugin.messages().send(sender, "general.unknown-subcommand",
                    Map.of("input", args[0], "suggestion", suggestion(args[0])));
            return true;
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return run(sender, subcommand.name(), rest);
    }

    /** Closest known subcommand, so a typo answers with what to type instead. */
    private String suggestion(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        for (String candidate : subcommands.keySet()) {
            if (candidate.startsWith(lower) || lower.startsWith(candidate)) {
                return candidate;
            }
        }
        return "help";
    }

    private boolean run(CommandSender sender, String name, String[] args) {
        Subcommand subcommand = subcommands.get(name.toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            return true;
        }
        if (!subcommand.permission().isEmpty() && !sender.hasPermission(subcommand.permission())) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        try {
            subcommand.execute(sender, args);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Command '/honeycomb " + name + "' failed: " + exception);
            plugin.messages().send(sender, "general.invalid-usage", Map.of("usage", subcommand.usage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Subcommand subcommand : unique()) {
                if (subcommand.permission().isEmpty() || sender.hasPermission(subcommand.permission())) {
                    names.add(subcommand.name());
                }
            }
            return StringUtil.copyPartialMatches(args[0], names, new ArrayList<>());
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            return Collections.emptyList();
        }
        if (!subcommand.permission().isEmpty() && !sender.hasPermission(subcommand.permission())) {
            return Collections.emptyList();
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return StringUtil.copyPartialMatches(rest[rest.length - 1],
                subcommand.complete(sender, rest), new ArrayList<>());
    }

    /**
     * @return registered subcommands without alias duplicates, in registration order
     */
    public List<Subcommand> unique() {
        List<Subcommand> result = new ArrayList<>();
        for (Subcommand subcommand : subcommands.values()) {
            if (!result.contains(subcommand)) {
                result.add(subcommand);
            }
        }
        return result;
    }

    /**
     * @param name subcommand name or alias
     * @return the subcommand, or {@code null}
     */
    public Subcommand find(String name) {
        return name == null ? null : subcommands.get(name.toLowerCase(Locale.ROOT));
    }
}
