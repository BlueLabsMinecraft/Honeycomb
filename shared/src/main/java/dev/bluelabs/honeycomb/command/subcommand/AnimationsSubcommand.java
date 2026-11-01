package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.animation.Animation;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists configured animations and previews their current frame.
 */
public final class AnimationsSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public AnimationsSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "animations";
    }

    @Override
    public String permission() {
        return "honeycomb.animations";
    }

    @Override
    public String usage() {
        return "animations";
    }

    @Override
    public String description() {
        return "Lists animations and previews the current frame";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        List<String> ids = plugin.animations().ids();
        sender.sendMessage(plugin.messages().component("command.animations-header",
                Map.of("count", Integer.toString(ids.size()))));
        PlaceholderContext context = sender instanceof Player player
                ? PlaceholderContext.of(player)
                : PlaceholderContext.global("");
        for (String id : ids) {
            Animation animation = plugin.animations().get(id);
            if (animation == null) {
                continue;
            }
            sender.sendMessage(plugin.messages().component("command.animations-line", Map.of(
                    "id", id,
                    "frames", Integer.toString(animation.frameCount()),
                    "interval", Integer.toString(animation.interval()))));
            String frame = plugin.pipeline().prepare("<anim:" + id + ">", context);
            sender.sendMessage(plugin.messages().component("command.animations-preview", Map.of(
                    "id", id,
                    "frame", frame)));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return new ArrayList<>(plugin.animations().ids());
    }
}
