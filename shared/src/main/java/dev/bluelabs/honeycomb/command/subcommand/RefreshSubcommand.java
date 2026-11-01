package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Re-applies displays to the sender or to one named player.
 */
public final class RefreshSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public RefreshSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "refresh";
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "refresh [player]";
    }

    @Override
    public String description() {
        return "Re-applies displays to you or a player";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.coordinator().refreshAll();
                plugin.messages().send(sender, "general.refresh-success",
                        Map.of("count", Integer.toString(Bukkit.getOnlinePlayers().size())));
                return;
            }
            plugin.coordinator().refresh(player);
            plugin.messages().send(sender, "general.refresh-success", Map.of("count", "1"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "general.invalid-usage", Map.of("usage", usage()));
            return;
        }
        plugin.coordinator().refresh(target);
        plugin.messages().send(sender, "general.refresh-success", Map.of("count", "1"));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
