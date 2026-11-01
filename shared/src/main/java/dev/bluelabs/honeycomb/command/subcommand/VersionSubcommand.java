package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.util.MinecraftVersions;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Prints the plugin, build and server versions.
 */
public final class VersionSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public VersionSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String permission() {
        return "honeycomb.use";
    }

    @Override
    public String usage() {
        return "version";
    }

    @Override
    public String description() {
        return "Shows plugin and server versions";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.messages().send(sender, "command.version", Map.of(
                "version", plugin.version(),
                "range", plugin.minecraftRange(),
                "server", MinecraftVersions.extractMinecraftVersion(plugin.getServer().getBukkitVersion()),
                "java", System.getProperty("java.version")));
    }
}
