package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows which nametag group every online player gets.
 *
 * <p>When nothing appears above a player's head, this is the fastest way to see
 * whether a group matched at all and what text it produces.</p>
 */
public final class NametagsSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public NametagsSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "nametags";
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "nametags";
    }

    @Override
    public String description() {
        return "Shows which nametag group each player matches";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        sender.sendMessage(Component.text("Nametag groups (" + players.size() + " player(s) online)",
                NamedTextColor.GOLD));
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("Nobody is online, so there is nothing to show.",
                    NamedTextColor.GRAY));
            return;
        }
        for (Player player : players) {
            String line = plugin.nametags().describe(player);
            sender.sendMessage(Component.text(" " + line,
                    line.contains("no group matched") || line.contains("disabled")
                            ? NamedTextColor.RED
                            : NamedTextColor.WHITE));
        }
        sender.sendMessage(Component.text("Groups are read from nametags.yml; a group with no"
                + " permission and the name 'default' catches everyone else.", NamedTextColor.DARK_GRAY));
    }
}
