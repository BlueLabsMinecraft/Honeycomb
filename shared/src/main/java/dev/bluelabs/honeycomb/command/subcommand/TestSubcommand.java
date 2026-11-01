package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Sends a real, visible test to the player and re-applies every display.
 */
public final class TestSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public TestSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String permission() {
        return "honeycomb.test";
    }

    @Override
    public String usage() {
        return "test";
    }

    @Override
    public String description() {
        return "Sends a test display to yourself";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        plugin.coordinator().refresh(player);

        int active = 0;
        for (DisplaySystem system : plugin.coordinator().systems()) {
            if (system.isEnabled()) {
                active++;
            }
        }
        if (active == 0) {
            plugin.messages().send(sender, "command.test-no-display", Map.of("system", "every display"));
            return;
        }

        Component title = MiniMessage.miniMessage().deserialize(
                "<gradient:#FADE29:#D56B2B><bold>Honeycomb</bold></gradient> <gray>display test");
        BossBar bar = BossBar.bossBar(title, 1.0F, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        plugin.tasks().later("test-bossbar", 100L, () -> {
            if (player.isOnline()) {
                player.hideBossBar(bar);
            }
        });
        plugin.messages().send(sender, "command.test-sent",
                Map.of("system", active + " display system(s) and a test bossbar"));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
