package dev.bluelabs.honeycomb.listener;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.display.DisplayCoordinator;
import dev.bluelabs.honeycomb.network.NetworkService;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps displays in step with player lifecycle events.
 *
 * <p>Joins are applied one tick later so other plugins have finished their own
 * join handling, and quits release every piece of per-player state immediately.</p>
 */
public final class PlayerConnectionListener implements Listener {

    private final PlayerSessionRegistry sessions;
    private final DisplayCoordinator coordinator;
    private final TaskRegistry tasks;
    private final NetworkService network;

    /**
     * @param sessions    player sessions
     * @param coordinator display coordinator
     * @param tasks       task registry
     * @param network     network service
     */
    public PlayerConnectionListener(PlayerSessionRegistry sessions, DisplayCoordinator coordinator,
                                    TaskRegistry tasks, NetworkService network) {
        this.sessions = sessions;
        this.coordinator = coordinator;
        this.tasks = tasks;
        this.network = network;
    }

    /**
     * @param event the join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sessions.get(event.getPlayer());
        tasks.nextTick("join-display", () -> {
            if (event.getPlayer().isOnline()) {
                coordinator.refresh(event.getPlayer());
            }
        });
        network.requestCounts();
    }

    /**
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        coordinator.forget(event.getPlayer());
        sessions.remove(event.getPlayer());
    }

    /**
     * @param event the world change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerSession session = sessions.get(event.getPlayer());
        session.world(event.getPlayer().getWorld().getName());
        coordinator.refresh(event.getPlayer());
    }

    /**
     * @param event the respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // A respawn installs a fresh scoreboard, so everything is re-applied.
        tasks.nextTick("respawn-display", () -> {
            if (event.getPlayer().isOnline()) {
                coordinator.refresh(event.getPlayer());
            }
        });
    }
}
