package dev.bluelabs.honeycomb.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesTest {

    @Test
    void extractsMinecraftVersions() {
        assertEquals("1.21.9", MinecraftVersions.extractMinecraftVersion("1.21.9-R0.1-SNAPSHOT"));
        assertEquals("26.1.2", MinecraftVersions.extractMinecraftVersion("26.1.2-51-abcdefg"));
        assertEquals("", MinecraftVersions.extractMinecraftVersion("snapshot"));
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(MinecraftVersions.compare("1.21.10", "1.21.9") > 0);
        assertTrue(MinecraftVersions.compare("26.1", "1.21.11") > 0);
        assertEquals(0, MinecraftVersions.compare("26.3", "26.3.0"));
    }

    @Test
    void recognisesSupportedRanges() {
        assertTrue(MinecraftVersions.isWithin("1.21.10", "1.21.9", "1.21.11"));
        assertTrue(MinecraftVersions.isSupported("1.21.11"));
        assertTrue(MinecraftVersions.isSupported("26.2"));
        assertFalse(MinecraftVersions.isSupported("1.20.4"));
        assertFalse(MinecraftVersions.isSupported("27.0"));
    }

    @Test
    void formatsDurations() {
        assertEquals("1m 5s", TimeFormats.compact(Duration.ofSeconds(65)));
        assertEquals("1h 2m 5s", TimeFormats.compact(Duration.ofSeconds(3725)));
        assertEquals("5s", TimeFormats.compact(Duration.ofSeconds(5)));
        assertEquals("01:01:01", TimeFormats.clock(3_661_000L));
    }

    @Test
    void formatsNumbers() {
        assertEquals("12.5k", Numbers.compact(12_500));
        assertEquals("999", Numbers.compact(999));
        assertEquals("1.5", Numbers.format(1.456, 1));
        assertEquals("5", Numbers.grouped(5));
        assertEquals(1.0D, Numbers.clamp(3.0D, 0.0D, 1.0D));
    }
}
