package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.diagnostics.TickMetrics;
import dev.bluelabs.honeycomb.placeholder.PlaceholderService;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import org.bukkit.Bukkit;
import dev.bluelabs.honeycomb.scheduler.Scheduling;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs every display from one scheduled task.
 *
 * <p>Honeycomb deliberately owns a single repeating task. It advances the shared
 * tick clock, then offers each player to each active system; the systems decide
 * internally whether that player is due. No system creates its own task, so a
 * reload cannot leak timers and a hundred players cost the same number of tasks as
 * one player.</p>
 */
public final class DisplayCoordinator {

    private static final String TASK_NAME = "displays";

    private final TaskRegistry tasks;
    private final TickClock clock;
    private final PlaceholderService placeholders;
    private final List<DisplaySystem> systems = new ArrayList<>();
    private TickMetrics metrics;

    /**
     * @param tasks        task registry
     * @param clock        shared tick clock
     * @param placeholders placeholder service, swept periodically
     */
    public DisplayCoordinator(TaskRegistry tasks, TickClock clock, PlaceholderService placeholders) {
        this.tasks = tasks;
        this.clock = clock;
        this.placeholders = placeholders;
    }

    /**
     * @param metrics optional receiver for timings and error counts
     */
    public void setMetrics(TickMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Adds a system. Order matters only for readability of status output.
     *
     * @param system the system
     */
    public void register(DisplaySystem system) {
        systems.add(system);
    }

    /**
     * Enables every registered system and starts the single display task.
     */
    public void start() {
        for (DisplaySystem system : systems) {
            system.enable();
        }
        tasks.repeating(TASK_NAME, 1L, 1L, this::runTick);
    }

    /**
     * Stops the task and disables every system, removing what it placed on clients.
     */
    public void stop() {
        tasks.cancel(TASK_NAME);
        for (DisplaySystem system : systems) {
            system.disable();
        }
    }

    /**
     * Reloads every system and re-applies it to online players.
     */
    public void reloadAll() {
        for (DisplaySystem system : systems) {
            system.reload();
        }
        refreshAll();
    }

    /**
     * Runs work for every online player on the scheduler that owns that player,
     * which is what makes the same code safe on Folia.
     *
     * @param action work to run per player
     */
    private void forEachOnline(Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduling.forPlayer(tasks.plugin(), player, () -> action.accept(player));
        }
    }

    /**
     * Re-applies all systems to every online player.
     */
    public void refreshAll() {
        forEachOnline(player -> {                for (DisplaySystem system : systems) {
                    if (system.isEnabled()) {
                        system.refresh(player);
                    }
                }
        });
    }

    /**
     * Re-applies all systems to one player.
     *
     * @param player the player
     */
    public void refresh(Player player) {
        for (DisplaySystem system : systems) {
            if (system.isEnabled()) {
                system.refresh(player);
            }
        }
    }

    /**
     * Lets every system drop a player's state.
     *
     * @param player the player who left
     */
    public void forget(Player player) {
        for (DisplaySystem system : systems) {
            system.forget(player);
        }
    }

    private void runTick() {
        long started = System.nanoTime();
        clock.advance();
        forEachOnline(player -> {                for (DisplaySystem system : systems) {
                    if (!system.isEnabled()) {
                        continue;
                    }
                    try {
                        system.tick(player);
                    } catch (RuntimeException exception) {
                        // One broken display must never stop the others updating.
                        warn(system.name(), player, exception);
                        if (metrics != null) {
                            metrics.recordError();
                        }
                    }
                }
        });
        if (metrics != null) {
            metrics.recordPass(System.nanoTime() - started);
        }
    }

    /**
     * @return the registered systems
     */
    public List<DisplaySystem> systems() {
        return Collections.unmodifiableList(systems);
    }

    private static final java.util.Map<String, Long> LAST_WARN = new java.util.HashMap<>();

    /**
     * Logs a display failure with its stack trace, at most once a minute per
     * player and display, so a repeating error cannot bury the log.
     *
     * @param system    display name
     * @param player    player being updated
     * @param exception the failure
     */
    private static void warn(String system, Player player, RuntimeException exception) {
        String key = system + "/" + player.getName();
        long now = System.currentTimeMillis();
        Long seen = LAST_WARN.get(key);
        if (seen != null && now - seen < 60_000L) {
            return;
        }
        LAST_WARN.put(key, now);
        Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                "[Honeycomb] " + system + " failed while updating " + player.getName(),
                exception);
    }
}
