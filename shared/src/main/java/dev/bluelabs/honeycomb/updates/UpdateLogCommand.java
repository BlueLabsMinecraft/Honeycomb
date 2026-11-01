package dev.bluelabs.honeycomb.updates;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the update log for players.
 */
public final class UpdateLogCommand implements CommandExecutor {

    private final HoneycombPlugin plugin;

    public UpdateLogCommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            if (plugin.panel() == null) {
                sender.sendMessage(net.kyori.adventure.text.Component.text(
                        "The update log needs Paper 1.21.9 or newer."));
                return true;
            }
            sender.sendMessage(Component.text("The update log opens in game.", NamedTextColor.RED));
            return true;
        }
        if (!plugin.updates().enabled()) {
            player.sendMessage(Component.text("The update log is turned off.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0) {
            if (plugin.updates().entry(args[0]) == null) {
                player.sendMessage(Component.text("No update called '" + args[0] + "'.", NamedTextColor.RED));
                return true;
            }
            plugin.panel().openUpdateEntry(player, args[0]);
            return true;
        }
        plugin.panel().openUpdateLog(player);
        return true;
    }

    /** Renders one line of an entry, which may use MiniMessage. */
    private static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text == null ? "" : text);
    }
}
