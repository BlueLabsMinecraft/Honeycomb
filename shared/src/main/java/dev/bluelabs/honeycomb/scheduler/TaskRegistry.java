package dev.bluelabs.honeycomb.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns every scheduler task Honeycomb creates.
 *
 * <p>Tasks are named, so a reload can cancel and restart exactly one system
 * without leaking the previous copy, and every task is cancelled when the plugin
 * disables.</p>
 */
public final class TaskRegistry {

    private final Plugin plugin;
    private final Map<String, Object> repeatingTasks = new LinkedHashMap<>();
    private final Map<String, Object> pendingTasks = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * @param plugin plugin owning the tasks
     */
    public TaskRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return the plugin that owns these tasks
     */
    public Plugin plugin() {
        return plugin;
    }

    /**
     * Starts a repeating task, replacing any previous task registered under the
     * same name.
     *
     * @param name     unique task name
     * @param delay    delay in ticks before the first run
     * @param period   period in ticks
     * @param runnable work to run
     */
    public void repeating(String name, long delay, long period, Runnable runnable) {
        cancel(name);
        Object task = Scheduling.repeat(plugin, delay, period, runnable);
        repeatingTasks.put(name, task);
    }

    /**
     * Starts a one-shot task.
     *
     * @param name   descriptive name used in diagnostics
     * @param delay  delay in ticks
     * @param runnable work to run
     * @return the task name, which can be passed to {@link #cancel(String)}
     */
    public String later(String name, long delay, Runnable runnable) {
        String key = name + "#" + sequence.incrementAndGet();
        Object task = Scheduling.later(plugin, delay, () -> {
            pendingTasks.remove(key);
            runnable.run();
        });
        pendingTasks.put(key, task);
        return key;
    }

    /**
     * Runs work on the next tick of the main thread.
     *
     * @param name     descriptive name
     * @param runnable work to run
     * @return the task name
     */
    public String nextTick(String name, Runnable runnable) {
        return later(name, 1L, runnable);
    }

    /**
     * Runs work away from the main thread. Used for file writes so a save can
     * never stall the server.
     *
     * @param name     descriptive name
     * @param runnable work to run
     */
    public void async(String name, Runnable runnable) {
        if (Scheduling.folia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    /**
     * Cancels a repeating task by name.
     *
     * @param name task name
     */
    public void cancel(String name) {
        Object task = repeatingTasks.remove(name);
        if (task != null) {
            Scheduling.cancel(task);
        }
    }

    /**
     * Cancels a one-shot task returned by {@link #later(String, long, Runnable)}.
     *
     * @param key task key
     */
    public void cancelPending(String key) {
        Object task = pendingTasks.remove(key);
        if (task != null) {
            Scheduling.cancel(task);
        }
    }

    /**
     * Cancels every tracked task. Safe to call more than once.
     */
    public void cancelAll() {
        for (Object task : repeatingTasks.values()) {
            Scheduling.cancel(task);
        }
        for (Object task : pendingTasks.values()) {
            Scheduling.cancel(task);
        }
        repeatingTasks.clear();
        pendingTasks.clear();
    }

    /**
     * @return number of tracked tasks, for diagnostics
     */
    public int activeTaskCount() {
        return repeatingTasks.size() + pendingTasks.size();
    }

    /**
     * @return the names of the repeating tasks, for diagnostics
     */
    public Set<String> repeatingTaskNames() {
        return Collections.unmodifiableSet(repeatingTasks.keySet());
    }

    /**
     * @return the names of pending one-shot tasks, for diagnostics
     */
    public List<String> pendingTaskNames() {
        return Collections.unmodifiableList(new ArrayList<>(pendingTasks.keySet()));
    }
}
