package dev.bluelabs.honeycomb.diagnostics;

/**
 * Receives measurements from the display loop.
 *
 * <p>Implemented by {@link DiagnosticsService}; kept as an interface so the display
 * coordinator does not depend on the whole diagnostics package.</p>
 */
public interface TickMetrics {

    /**
     * Records how long one display pass took.
     *
     * @param nanos duration in nanoseconds
     */
    void recordPass(long nanos);

    /**
     * Records that one display system failed for one player.
     */
    void recordError();
}
