package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Opens the in-game configuration panel.
 *
 * <p>Dialog screens need Minecraft 1.21.9 or newer. The subcommand exists on older
 * servers too so tab completion and help stay consistent; opening a screen simply
 * reports that dialogs are unavailable.</p>
 */
public final class PanelSubcommand implements Subcommand {

    private static final List<String> SECTIONS = List.of("displays", "server", "setup", "maintenance",
            "tablist", "scoreboard", "actionbar", "nametags", "bossbars", "welcome", "brand", "commands",
            "messages", "network", "animations", "staff", "presets", "updates", "debug");

    private final HoneycombPlugin plugin;

    public PanelSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "panel";
    }

    @Override
    public List<String> aliases() {
        return List.of("menu", "config");
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "panel";
    }

    @Override
    public String description() {
        return "Open the in game editor";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String requested = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "main";
        if (requested.equals("new") && args.length >= 3) {
            // Layouts can be added without a dialog, which also makes the panel's Create
            // button testable from the console.
            String kind = args[1].toLowerCase(Locale.ROOT);
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            sender.sendMessage(Component.text(plugin.panel().createEntry(kind, name),
                    NamedTextColor.WHITE));
            return;
        }
        if (requested.equals("toggle") && args.length >= 3) {
            sender.sendMessage(Component.text(plugin.panel().toggleEntry(args[1], args[2]),
                    NamedTextColor.WHITE));
            return;
        }
        if (requested.equals("use") && args.length >= 3) {
            sender.sendMessage(Component.text(plugin.panel().selectEntry(args[1], args[2]),
                    NamedTextColor.WHITE));
            return;
        }
        if (requested.equals("debug")) {
            // Works from the console: proves every dialog builds before a player sees it.
            sender.sendMessage(Component.text("Building panel dialogs...", NamedTextColor.GRAY));
            for (String line : plugin.panel().selfTest()) {
                boolean failed = line.contains("FAILED");
                sender.sendMessage(Component.text(line, failed ? NamedTextColor.RED : NamedTextColor.GREEN));
            }
            return;
        }
        if (plugin.panel() == null) {
            sender.sendMessage(Component.text("The dialog panel needs Paper 1.21.9 or newer."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("The configuration panel can only be opened in game.",
                    NamedTextColor.RED));
            return;
        }
        String section = requested;
        if (section.equals("close")) {
            return;
        }
        plugin.panel().open(player, section);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return SECTIONS;
        }
        return List.of();
    }
}
