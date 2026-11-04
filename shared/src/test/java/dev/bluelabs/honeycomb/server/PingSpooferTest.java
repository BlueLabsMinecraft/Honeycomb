package dev.bluelabs.honeycomb.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The number displays show for ping, with spoofing off, on and in each mode.
 */
class PingSpooferTest {

    @Test
    void offMeansTheMeasuredPingIsUsed() {
        PingSpoofer spoofer = new PingSpoofer(false, "fixed", 24, 15, 45);
        assertFalse(spoofer.enabled());
        assertEquals(87, spoofer.display(null, 87));
    }

    @Test
    void fixedShowsTheSameNumberToEveryone() {
        PingSpoofer spoofer = new PingSpoofer(true, "fixed", 24, 15, 45);
        assertTrue(spoofer.enabled());
        assertEquals(24, spoofer.display(null, 200));
        assertEquals(24, spoofer.display(null, 3));
    }

    @Test
    void rangeStaysInsideItsBounds() {
        PingSpoofer spoofer = new PingSpoofer(true, "range", 0, 15, 45);
        for (int i = 0; i < 200; i++) {
            int shown = spoofer.display(null, 900);
            assertTrue(shown >= 15 && shown <= 45, "out of range: " + shown);
        }
    }

    @Test
    void jitterStaysInsideItsBounds() {
        PingSpoofer spoofer = new PingSpoofer(true, "jitter", 0, 10, 12);
        for (int i = 0; i < 200; i++) {
            int shown = spoofer.display(null, 900);
            assertTrue(shown >= 10 && shown <= 12, "out of range: " + shown);
        }
    }

    @Test
    void aReversedRangeIsForgiven() {
        PingSpoofer spoofer = new PingSpoofer(true, "range", 0, 45, 15);
        for (int i = 0; i < 100; i++) {
            int shown = spoofer.display(null, 900);
            assertTrue(shown >= 15 && shown <= 45, "out of range: " + shown);
        }
    }

    @Test
    void aBrokenModeFallsBackToTheFixedValue() {
        PingSpoofer spoofer = new PingSpoofer(true, "nonsense", 33, 15, 45);
        assertEquals(33, spoofer.display(null, 200));
    }
}
