package dev.bluelabs.honeycomb.display.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSidebarTest {

    @Test
    void entriesAreUniqueAcrossTheFifteenRows() {
        Set<String> entries = new HashSet<>();
        for (int index = 0; index < ScoreboardProfile.MAX_LINES; index++) {
            entries.add(PlayerSidebar.entryFor(index));
        }
        assertEquals(ScoreboardProfile.MAX_LINES, entries.size());
    }

    @Test
    void entriesAreInvisibleToThePlayer() {
        String entry = PlayerSidebar.entryFor(3);
        assertTrue(entry.startsWith("\u00A7"));
        assertTrue(entry.endsWith("\u00A7r"));
    }
}
