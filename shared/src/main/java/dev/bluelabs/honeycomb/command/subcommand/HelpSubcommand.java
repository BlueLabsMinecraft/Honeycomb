package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists every subcommand the sender may use.
 */
public final class HelpSubcommand implements Subcommand {

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
        for (Subcommand subcommand : plugin.commandRegistry().command().unique()) {
            if (!subcommand.permission().isEmpty() && !sender.hasPermission(subcommand.permission())) {
                continue;
            }
            sender.sendMessage(plugin.messages().component("command.help-line", Map.of(
                    "label", "honeycomb",
                    "usage", subcommand.usage(),
                    "description", subcommand.description())));
        }
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
