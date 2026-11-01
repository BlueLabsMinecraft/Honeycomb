package dev.bluelabs.honeycomb.scheduler;

/**
 * A single monotonic tick counter shared by every Honeycomb system.
 *
 * <p>Animations, caches and display rotations read this counter instead of each
 * owning a repeating task, which keeps the plugin at one lightweight task no
 * matter how many displays or players exist.</p>
 */
public final class TickClock {

    private long tick;

    /**
     * @return the current tick
     */
    public long tick() {
        return tick;
    }

    /**
     * Advances the clock by one tick. Called from the central scheduler only.
     */
    public void advance() {
        tick++;
    }

    /**
     * Tests whether a task with the given interval is due on this tick, offset by
     * a stable per-entity value so all players are not refreshed in the same tick.
     *
     * @param interval tick interval, one or greater
     * @param offset   stable offset, for example a player's id hash
     * @return true when the caller should update now
     */
    public boolean isDue(int interval, int offset) {
        int safeInterval = Math.max(1, interval);
        if (safeInterval == 1) {
            return true;
        }
        return Math.floorMod(tick + offset, safeInterval) == 0;
    }
}
