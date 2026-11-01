package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * Loads and saves ready made layouts.
 */
public final class PresetSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public PresetSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "preset";
    }

    @Override
    public List<String> aliases() {
        return List.of("presets", "layout");
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "preset <list|load|save> [name]";
    }

    @Override
    public String description() {
        return "Load or save a ready made tablist and scoreboard layout";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
        switch (action) {
            case "list" -> list(sender);
            case "load" -> {
                if (id == null) {
                    sender.sendMessage(Component.text("Which preset? Try /honeycomb preset list", NamedTextColor.RED));
                    return;
                }
                plugin.presets().load(id, sender);
            }
            case "save" -> {
                if (id == null) {
                    sender.sendMessage(Component.text("Name the preset, for example /honeycomb preset save mine",
                            NamedTextColor.RED));
                    return;
                }
                plugin.presets().save(id, sender);
            }
            default -> sender.sendMessage(Component.text(
                    "Use /honeycomb preset list, load <name> or save <name>", NamedTextColor.RED));
        }
    }

    private void list(CommandSender sender) {
        List<String> names = plugin.presets().names();
        if (names.isEmpty()) {
            sender.sendMessage(Component.text("No presets found. /honeycomb preset save mine makes one.",
                    NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Presets:", NamedTextColor.GOLD));
        for (String name : names) {
            String description = plugin.presets().description(name);
            sender.sendMessage(Component.text("  " + name, NamedTextColor.WHITE)
                    .append(Component.text(description.isBlank() ? "" : "  " + description, NamedTextColor.GRAY)));
        }
        sender.sendMessage(Component.text("Load one with /honeycomb preset load <name>", NamedTextColor.GRAY));
    }
}
