package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.config.ConfigType;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.Locale;

/**
 * Turns maintenance mode on or off without opening a dialog.
 *
 * <p>The panel edits the same value, and this exists so the switch can be flipped
 * from console, a script, or when a client cannot open dialogs.</p>
 */
public final class MaintenanceSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public MaintenanceSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "maintenance";
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "maintenance <on|off|status>";
    }

    @Override
    public String description() {
        return "Turns maintenance mode on or off";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        boolean current = plugin.serverList().maintenance();
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(plugin.messages().component("general.maintenance-status")
                    .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(net.kyori.adventure.text.Component.text(
                            current ? " on" : " off",
                            current ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                                    : net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            return;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        boolean wanted;
        if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
            wanted = true;
        } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
            wanted = false;
        } else {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "Use /honeycomb maintenance on, off or status.", NamedTextColorOf.RED));
            return;
        }
        plugin.panelOverrides().set(ConfigType.MAINTENANCE, "maintenance.enabled", wanted);
        try {
            plugin.panelOverrides().save();
        } catch (IOException failure) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "Could not write panel.yml: " + failure.getMessage(),
                    NamedTextColorOf.RED));
            return;
        }
        plugin.reload();
        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "Maintenance is now " + (wanted ? "ON" : "OFF") + ".",
                wanted ? NamedTextColorOf.GREEN : NamedTextColorOf.GRAY));
    }

    /** Colour shortcuts keep the lines above readable. */
    private static final class NamedTextColorOf {
        static final net.kyori.adventure.text.format.TextColor RED =
                net.kyori.adventure.text.format.NamedTextColor.RED;
        static final net.kyori.adventure.text.format.TextColor GREEN =
                net.kyori.adventure.text.format.NamedTextColor.GREEN;
        static final net.kyori.adventure.text.format.TextColor GRAY =
                net.kyori.adventure.text.format.NamedTextColor.GRAY;
    }
}
