package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Shows which displays are active and how they are configured.
 */
public final class StatusSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public StatusSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String permission() {
        return "honeycomb.status";
    }

    @Override
    public String usage() {
        return "status";
    }

    @Override
    public String description() {
        return "Shows display and integration status";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.messages().component("command.status-header"));
        for (DisplaySystem system : plugin.coordinator().systems()) {
            sender.sendMessage(plugin.messages().component("command.status-line", Map.of(
                    "name", system.name(),
                    "state", system.isEnabled()
                            ? plugin.messages().raw("command.status-enabled")
                            : plugin.messages().raw("command.status-disabled"),
                    "detail", system.describe())));
        }
        sender.sendMessage(plugin.messages().component("command.status-line", Map.of(
                "name", "network",
                "state", plugin.network().sourceName(),
                "detail", plugin.network().available()
                        ? "online " + plugin.network().onlineEverywhere()
                        : "no proxy answer")));
        sender.sendMessage(plugin.messages().component("command.status-line", Map.of(
                "name", "tasks",
                "state", Integer.toString(plugin.tasks().activeTaskCount()),
                "detail", plugin.tasks().repeatingTaskNames().toString())));
    }
}
