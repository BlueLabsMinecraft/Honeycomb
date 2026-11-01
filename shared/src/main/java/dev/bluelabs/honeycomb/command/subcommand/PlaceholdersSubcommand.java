package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.placeholder.InternalPlaceholders;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows what every built-in placeholder currently resolves to for the sender.
 */
public final class PlaceholdersSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public PlaceholdersSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "placeholders";
    }

    @Override
    public String permission() {
        return "honeycomb.placeholders";
    }

    @Override
    public String usage() {
        return "placeholders";
    }

    @Override
    public String description() {
        return "Shows resolved placeholder values";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        PlaceholderContext context = sender instanceof Player player
                ? PlaceholderContext.of(player)
                : PlaceholderContext.global("");
        sender.sendMessage(plugin.messages().component("command.placeholders-header",
                Map.of("player", sender.getName())));

        List<String> parts = new ArrayList<>();
        for (String id : InternalPlaceholders.documentedIds()) {
            String rendered = plugin.pipeline().prepare("%" + id + "%", context);
            // Unresolved placeholders come back unchanged; skip them so the list stays readable.
            if (rendered.equals("%" + id + "%")) {
                continue;
            }
            parts.add("<gray>" + id + "<dark_gray>=<white>" + rendered);
        }
        StringBuilder line = new StringBuilder();
        int perLine = 2;
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0 && index % perLine == 0) {
                sender.sendMessage(plugin.messages().component("command.debug-line",
                        Map.of("line", line.toString())));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(" <dark_gray>| ");
            }
            line.append(parts.get(index));
        }
        if (line.length() > 0) {
            sender.sendMessage(plugin.messages().component("command.debug-line",
                    Map.of("line", line.toString())));
        }
        if (!plugin.placeholders().unknownPlaceholders().isEmpty()) {
            sender.sendMessage(plugin.messages().component("command.placeholders-unknown",
                    Map.of("list", plugin.placeholders().unknownPlaceholders().toString())));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return new ArrayList<>(InternalPlaceholders.documentedIds());
    }
}
