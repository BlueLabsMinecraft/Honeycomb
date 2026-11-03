package dev.bluelabs.honeycomb.server;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maintenance mode and the server list entry: MOTD, counts and the software name.
 *
 * <p>The MOTD is a two line legacy string, so MiniMessage is translated to section
 * codes once and cached. The player count and the software name are not part of the
 * Bukkit event on every platform, so they are set through reflection and skipped
 * quietly when the running server does not offer them.</p>
 */
public final class ServerListService implements Listener {

    private final HoneycombPlugin plugin;

    private volatile boolean maintenance;
    private volatile String bypass = "honeycomb.maintenance.bypass";
    private volatile List<String> kickLines = List.of();
    private volatile boolean motdEnabled;
    private volatile String lineOne = "";
    private volatile String lineTwo = "";
    private volatile int fakeOnline = -1;
    private volatile int fakeMax = -1;
    private volatile String fakeSoftware = "";

    /**
     * @param plugin plugin instance
     */
    public ServerListService(HoneycombPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads maintenance.yml. Called on enable and after every reload. */
    public void reload() {
        var yaml = plugin.configManager().yaml(ConfigType.MAINTENANCE);
        maintenance = yaml.getBoolean("maintenance.enabled", false);
        bypass = yaml.getString("maintenance.bypass-permission", "honeycomb.maintenance.bypass");
        kickLines = new ArrayList<>(yaml.getStringList("maintenance.kick-message"));
        motdEnabled = yaml.getBoolean("motd.enabled", false);
        lineOne = yaml.getString("motd.line-1", "");
        lineTwo = yaml.getString("motd.line-2", "");
        fakeOnline = yaml.getInt("motd.online", -1);
        fakeMax = yaml.getInt("motd.max", -1);
        fakeSoftware = yaml.getString("motd.fake-software", "");
    }

    /**
     * @return true when maintenance mode is on
     */
    public boolean maintenance() {
        return maintenance;
    }

    /**
     * Stops a player joining while maintenance is on.
     *
     * @param event the login event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!maintenance) {
            return;
        }
        Player player = event.getPlayer();
        if (!bypass.isEmpty() && player.hasPermission(bypass)) {
            return;
        }
        Component reason = join(kickLines);
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, reason);
    }

    /**
     * Rewrites the server list entry.
     *
     * @param event the ping event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (motdEnabled) {
            int online = fakeOnline >= 0 ? fakeOnline : event.getNumPlayers();
            int max = fakeMax >= 0 ? fakeMax : event.getMaxPlayers();
            String first = fill(lineOne, online, max);
            String second = fill(lineTwo, online, max);
            event.setMotd(legacy(first) + (second.isEmpty() ? "" : "\n" + legacy(second)));
            if (fakeMax >= 0) {
                event.setMaxPlayers(fakeMax);
            }
            setIfPresent(event, "setNumPlayers", online);
        }
        if (!fakeSoftware.isBlank()) {
            setIfPresent(event, "setVersion", Text.plain(fill(fakeSoftware, event.getNumPlayers(),
                    event.getMaxPlayers())));
        }
    }

    private static String fill(String template, int online, int max) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return template.replace("{online}", String.valueOf(online)).replace("{max}", String.valueOf(max));
    }

    private static String legacy(String miniMessage) {
        Component component = Text.parse(miniMessage);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private static Component join(List<String> lines) {
        if (lines.isEmpty()) {
            return Component.text("Maintenance");
        }
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            out = out.append(Text.parse(lines.get(i)));
            if (i + 1 < lines.size()) {
                out = out.append(Component.newline());
            }
        }
        return out;
    }

    /** Calls an event setter that only some server platforms provide. */
    private static void setIfPresent(ServerListPingEvent event, String method, Object value) {
        try {
            event.getClass().getMethod(method, value instanceof String ? String.class : int.class)
                    .invoke(event, value);
        } catch (ReflectiveOperationException absent) {
            // The platform does not expose it; the rest of the entry still applies.
        }
    }

    /**
     * @return the values shown in the panel, for diagnostics
     */
    public Map<String, Object> snapshot() {
        return Map.of("maintenance", maintenance, "motd", motdEnabled,
                "online", fakeOnline, "max", fakeMax, "software", fakeSoftware);
    }
}
