package dev.bluelabs.honeycomb.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Schedules work on a Paper server, a Folia server or a plain Bukkit server.
 *
 * <p>Folia replaces the single main thread with region threads, so the Bukkit
 * scheduler is unavailable there and entity work has to run on the scheduler that
 * owns the entity. The check is a class lookup, so the same jar runs everywhere.</p>
 */
public final class Scheduling {

    private static final boolean FOLIA = detectFolia();

    private Scheduling() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    /**
     * @return true when the server runs the Folia threaded region scheduler
     */
    public static boolean folia() {
        return FOLIA;
    }

    /**
     * Starts a repeating task on the closest thing to a main thread.
     *
     * @param plugin  plugin owning the task
     * @param delay   delay in ticks
     * @param period  period in ticks
     * @param runnable work to run
     * @return the task handle, pass it to {@link #cancel(Object)}
     */
    public static Object repeat(Plugin plugin, long delay, long period, Runnable runnable) {
        long d = Math.max(1L, delay);
        long p = Math.max(1L, period);
        if (FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), d, p);
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, d, p);
    }

    /**
     * Starts a one shot task.
     *
     * @param plugin   plugin owning the task
     * @param delay    delay in ticks
     * @param runnable work to run
     * @return the task handle
     */
    public static Object later(Plugin plugin, long delay, Runnable runnable) {
        long d = Math.max(1L, delay);
        if (FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), d);
        }
        return Bukkit.getScheduler().runTaskLater(plugin, runnable, d);
    }

    /**
     * Runs work on the thread that owns the player.
     *
     * @param plugin   plugin owning the task
     * @param player   player whose scheduler is used on Folia
     * @param runnable work to run
     */
    public static void forPlayer(Plugin plugin, Player player, Runnable runnable) {
        if (!FOLIA) {
            runnable.run();
            return;
        }
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    /**
     * Runs work for every online player on the thread that owns each of them.
     *
     * @param plugin   plugin owning the tasks
     * @param action   work to run per player
     */
    public static void forEachPlayer(Plugin plugin, Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            forPlayer(plugin, player, () -> action.accept(player));
        }
    }

    /**
     * Cancels a handle returned by this class.
     *
     * @param handle task handle, may be {@code null}
     */
    public static void cancel(Object handle) {
        if (handle instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
        } else if (handle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled) {
            scheduled.cancel();
        }
    }
}
