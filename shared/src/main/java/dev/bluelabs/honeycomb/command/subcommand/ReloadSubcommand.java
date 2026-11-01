package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Reloads every configuration file and re-applies displays.
 */
public final class ReloadSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public ReloadSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "honeycomb.reload";
    }

    @Override
    public String usage() {
        return "reload";
    }

    @Override
    public String description() {
        return "Reloads all configuration files";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.messages().send(sender, "general.reloading");
        HoneycombPlugin.ReloadResult result = plugin.reload();
        Map<String, String> placeholders = Map.of(
                "time", Long.toString(result.millis()),
                "summary", result.report().summary());
        if (result.report().hasErrors()) {
            plugin.messages().send(sender, "general.reload-failed", placeholders);
            return;
        }
        plugin.messages().send(sender, "general.reload-success", placeholders);
    }
}
