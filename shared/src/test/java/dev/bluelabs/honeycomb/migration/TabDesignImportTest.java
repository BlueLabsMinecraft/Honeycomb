package dev.bluelabs.honeycomb.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabDesignImportTest {

    @Test
    void readsWorldsFromDesignCondition() {
        assertEquals(List.of("spawn", "arenas"), TabMigration.worlds("%world%=spawn|%world%=arenas"));
    }

    @Test
    void readsThreeWorldCondition() {
        assertEquals(List.of("oneblock_world", "oneblock_world_nether", "oneblock_world_the_end"),
                TabMigration.worlds("%world%=oneblock_world|%world%=oneblock_world_nether|%world%=oneblock_world_the_end"));
    }

    @Test
    void ignoresConditionsWithoutWorlds() {
        assertTrue(TabMigration.worlds("''").isEmpty());
        assertTrue(TabMigration.worlds("%pvpcore_in_match%=true").isEmpty());
        assertTrue(TabMigration.worlds(null).isEmpty());
    }

    @Test
    void turnsAnimationPlaceholderIntoTagon() {
        assertEquals("<shadow:#000000FF><anim:infoturnstilelabels>",
                TabMigration.finish("<shadow:#000000FF>%animation:InfoTurnstileLabels%"));
    }

    @Test
    void turnsLegacyHexIntoMiniMessage() {
        assertEquals("<#FF007C>PLAY", TabMigration.finish("&#FF007C PLAY".replace(" ", "")));
    }

    @Test
    void slugsIdsSafely() {
        assertEquals("battle-royale", TabMigration.slug("Battle Royale"));
        assertEquals("default", TabMigration.slug("  "));
    }
}
