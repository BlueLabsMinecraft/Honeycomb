package dev.bluelabs.honeycomb.server;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import dev.bluelabs.honeycomb.HoneycombPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hides selected commands from players who are not staff, both when they run them and
 * when they tab complete.
 *
 * <p>Nothing is unregistered, so the commands still work for staff and for the console.
 * The reply is whatever the owner configured, so a hidden command can look like an
 * ordinary refusal rather than a missing plugin.</p>
 */
public final class CommandHider implements Listener {

    private static final Set<String> VANILLA = Set.of("pl", "plugins", "version", "ver", "about", "icanhasbukkit");
    private static final String VIEW_PERMISSION = "honeycomb.commands.view";

    private final HoneycombPlugin plugin;
    private final Set<String> hidden = new HashSet<>();
    private String reply = "<red>You can't see that!";
    private boolean hideFromTab = true;
    private boolean hidePluginList;
    private boolean hideAllPlugins = true;
    private final Set<String> hiddenPlugins = new HashSet<>();

    public CommandHider(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    /** Re-reads the hidden list and reply text from config.yml. */
    public void reload() {
        hidden.clear();
        var yaml = plugin.configManager().yaml(dev.bluelabs.honeycomb.config.ConfigType.CONFIG);
        boolean vanilla = yaml.getBoolean("commands.hide-pl-version", true);
        if (vanilla) {
            hidden.addAll(VANILLA);
        }
        for (String extra : yaml.getStringList("commands.hidden")) {
            hidden.add(normalise(extra));
        }
        hideFromTab = yaml.getBoolean("commands.hide-from-tab", true);
        hidePluginList = yaml.getBoolean("commands.hide-plugins", false);
        // Hiding only the names in a list left every other plugin on show, which tells a
        // curious player exactly which software the server runs. Default is now everything.
        hideAllPlugins = yaml.getBoolean("commands.hide-all-plugins", true);
        hiddenPlugins.clear();
        for (String name : yaml.getStringList("commands.hide-plugin-names")) {
            String clean = name.toLowerCase(Locale.ROOT);
            if (clean.equals("*")) {
                hideAllPlugins = true;
            } else if (!clean.isBlank()) {
                hiddenPlugins.add(clean);
            }
        }
        String message = yaml.getString("commands.hidden-message");
        if (message != null && !message.isBlank()) {
            reply = message;
        }
    }

    /** @return the commands currently hidden from non staff */
    public Set<String> hidden() {
        return Set.copyOf(hidden);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(VIEW_PERMISSION)) {
            return;
        }
        String label = normalise(event.getMessage().startsWith("/")
                ? event.getMessage().substring(1)
                : event.getMessage());
        if (!hidden.contains(label)) {
            return;
        }
        event.setCancelled(true);
        if (isPluginList(label) && hidePluginList) {
            player.sendMessage(pluginList());
            return;
        }
        if (isVersion(label)) {
            // A refusal reads like a locked door; the version on its own gives nothing away.
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>This server is running Minecraft <white>" + minecraft()));
            return;
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(reply));
    }

    /**
     * @param label normalised command label
     * @return true for the commands that print the plugin list
     */
    private static boolean isPluginList(String label) {
        return label.equals("plugins") || label.equals("pl");
    }

    /**
     * @param label normalised command label
     * @return true for the commands that print version information
     */
    private static boolean isVersion(String label) {
        return label.equals("version") || label.equals("ver") || label.equals("about")
                || label.equals("icanhasbukkit");
    }

    /**
     * @return the plain Minecraft version, never the server brand or plugin list
     */
    private static String minecraft() {
        try {
            return org.bukkit.Bukkit.getMinecraftVersion();
        } catch (RuntimeException unavailable) {
            return "an unknown version";
        }
    }

    /**
     * Builds a plugin list with the configured names left out.
     *
     * <p>Other servers and tools read this list to work out what a server runs. When
     * {@code commands.hide-plugins} is on, the hidden names are simply not part of the
     * answer, so the reply still looks like an ordinary plugin list. With
     * {@code commands.hide-all-plugins} every plugin is left out, which is what the option
     * did for Honeycomb alone before.</p>
     *
     * @return the reply to send
     */
    private Component pluginList() {
        List<String> shown = new ArrayList<>();
        for (org.bukkit.plugin.Plugin loaded : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (hideAllPlugins) {
                continue;
            }
            if (!hiddenPlugins.contains(loaded.getName().toLowerCase(Locale.ROOT))) {
                shown.add((loaded.isEnabled() ? "<green>" : "<red>") + loaded.getName());
            }
        }
        shown.sort(String::compareToIgnoreCase);
        String list = shown.isEmpty() ? "<gray>none" : String.join("<gray>, ", shown);
        return MiniMessage.miniMessage().deserialize("<gold>Plugins (" + shown.size() + "): <reset>" + list);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        if (!hideFromTab || hidden.isEmpty()) {
            return;
        }
        if (!(event.getSender() instanceof Player player) || player.hasPermission(VIEW_PERMISSION)) {
            return;
        }
        if (event.getBuffer().startsWith("/")) {
            List<String> kept = new ArrayList<>();
            for (String completion : event.getCompletions()) {
                if (!hidden.contains(normalise(completion))) {
                    kept.add(completion);
                }
            }
            event.setCompletions(kept);
        }
    }

    /**
     * Reduces a command line, alias or suggestion to the plain label: leading slash,
     * arguments and a {@code bukkit:} style namespace are all removed.
     */
    private static String normalise(String input) {
        String value = MiniMessage.miniMessage().stripTags(input).trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int space = value.indexOf(' ');
        if (space > 0) {
            value = value.substring(0, space);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            value = value.substring(colon + 1);
        }
        return value;
    }
}
