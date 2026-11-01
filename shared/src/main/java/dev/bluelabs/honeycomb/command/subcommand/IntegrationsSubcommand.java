package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.integration.IntegrationManager;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Lists every optional integration and whether it is being used.
 */
public final class IntegrationsSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public IntegrationsSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "integrations";
    }

    @Override
    public String permission() {
        return "honeycomb.status";
    }

    @Override
    public String usage() {
        return "integrations";
    }

    @Override
    public String description() {
        return "Lists optional plugin integrations";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.messages().component("command.integrations-header"));
        for (IntegrationManager.Status status : plugin.integrations().statuses()) {
            sender.sendMessage(plugin.messages().component("command.integrations-line", Map.of(
                    "name", status.name(),
                    "state", status.available()
                            ? plugin.messages().raw("command.status-enabled")
                            : plugin.messages().raw("command.status-disabled"),
                    "detail", status.detail())));
        }
    }
}
