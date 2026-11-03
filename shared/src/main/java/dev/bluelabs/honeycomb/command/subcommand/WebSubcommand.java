package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.web.WebCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Imports a code made in the web editor.
 *
 * <p>The web editor is a static page, so the only thing that crosses over is the code
 * it prints. This command unpacks it and hands the sections to the panel, where the
 * admin ticks the ones they want applied.</p>
 */
public final class WebSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public WebSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "web";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "web <code>";
    }

    @Override
    public String description() {
        return "Import a setup made in the web editor";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("The import screen can only be opened in game.",
                    NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Paste the code after the command: /hc web hc1-...",
                    NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Make one at honey.gamer.gd/web-editor",
                    NamedTextColor.GRAY));
            return;
        }
        String code = String.join("", args);
        Map<String, Object> sections;
        try {
            sections = WebCodec.decode(code);
        } catch (IllegalArgumentException broken) {
            player.sendMessage(Component.text("That code could not be read: " + broken.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        if (sections.isEmpty()) {
            player.sendMessage(Component.text("That code contains no settings.", NamedTextColor.RED));
            return;
        }
        if (plugin.panel() == null) {
            player.sendMessage(Component.text("The import screen needs Paper 1.21.9 or newer.",
                    NamedTextColor.RED));
            return;
        }
        plugin.panel().openWeb(player, sections);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
