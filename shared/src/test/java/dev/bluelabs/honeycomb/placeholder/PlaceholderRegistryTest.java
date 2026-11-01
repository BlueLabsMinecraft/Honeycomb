package dev.bluelabs.honeycomb.placeholder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderRegistryTest {

    @Test
    void idsAreCaseInsensitive() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("Player_Name", context -> "Blue");
        assertEquals("Blue", registry.resolve("player_name", PlaceholderContext.global("world")));
        assertTrue(registry.contains("PLAYER_NAME"));
    }

    @Test
    void unknownIdsResolveToNull() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        assertNull(registry.resolve("nope", PlaceholderContext.global("world")));
    }

    @Test
    void unregisterRemovesPlaceholder() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("x", context -> "1");
        assertTrue(registry.unregister("X"));
        assertFalse(registry.unregister("x"));
        assertEquals(0, registry.size());
    }

    @Test
    void blankIdsAreIgnored() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("  ", context -> "1");
        assertEquals(0, registry.size());
    }
}
