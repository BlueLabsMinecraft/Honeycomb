package dev.bluelabs.honeycomb.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after Honeycomb has reloaded its configuration and re-applied displays.
 *
 * <p>Listen to this event if your plugin caches text that came from Honeycomb.</p>
 */
public final class HoneycombReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long durationMillis;
    private final int warningCount;
    private final int errorCount;

    /**
     * @param durationMillis how long the reload took
     * @param warningCount   configuration warnings raised
     * @param errorCount     configuration errors raised
     */
    public HoneycombReloadEvent(long durationMillis, int warningCount, int errorCount) {
        this.durationMillis = durationMillis;
        this.warningCount = warningCount;
        this.errorCount = errorCount;
    }

    /**
     * @return reload duration in milliseconds
     */
    public long durationMillis() {
        return durationMillis;
    }

    /**
     * @return number of configuration warnings
     */
    public int warningCount() {
        return warningCount;
    }

    /**
     * @return number of configuration errors
     */
    public int errorCount() {
        return errorCount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return the handler list, required by Bukkit
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
