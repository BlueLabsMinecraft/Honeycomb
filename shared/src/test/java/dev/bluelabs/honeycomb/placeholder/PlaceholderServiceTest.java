package dev.bluelabs.honeycomb.placeholder;

import dev.bluelabs.honeycomb.scheduler.TickClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderServiceTest {

    private PlaceholderRegistry registry;
    private PlaceholderService service;
    private TickClock clock;

    @BeforeEach
    void setUp() {
        registry = new PlaceholderRegistry();
        clock = new TickClock();
        service = new PlaceholderService(registry, clock);
        service.configure(10, true);
        registry.register("player_name", context -> "Blue");
        registry.register("server_online", context -> "7");
        registry.register("player_world", PlaceholderContext::worldName);
    }

    @Test
    void expandsKnownPlaceholders() {
        assertEquals("Hello Blue, 7 online",
                service.expand("Hello %player_name%, %server_online% online", PlaceholderContext.global("world")));
    }

    @Test
    void leavesUnknownPlaceholdersVisible() {
        assertEquals("%player_unknown%", service.expand("%player_unknown%", PlaceholderContext.global("world")));
    }

    @Test
    void recordsUnknownInternalPlaceholders() {
        service.expand("%player_unknown%", PlaceholderContext.global("world"));
        service.expand("%some_external_thing%", PlaceholderContext.global("world"));
        assertTrue(service.unknownPlaceholders().contains("player_unknown"));
        assertFalse(service.unknownPlaceholders().contains("some_external_thing"));
    }

    @Test
    void keepsUnpairedPercentSigns() {
        assertEquals("100% done %", service.expand("100% done %", PlaceholderContext.global("world")));
    }

    @Test
    void resolvesWorldFromContext() {
        assertEquals("world_nether", service.expand("%player_world%", PlaceholderContext.global("world_nether")));
    }

    @Test
    void handsUnresolvedTextToTheExternalProvider() {
        service.setExternalResolver((player, input) -> input.replace("%external%", "from-papi"));
        assertEquals("from-papi Blue", service.expand("%external% %player_name%", PlaceholderContext.global("world")));
    }

    @Test
    void cachesRepeatedExpansionsUntilExpired() {
        String first = service.expandCached("%server_online%", PlaceholderContext.global("world"));
        registry.register("server_online", context -> "99");
        String second = service.expandCached("%server_online%", PlaceholderContext.global("world"));
        assertEquals(first, second);
        assertEquals(1, service.cacheSize());

        for (int tick = 0; tick < 11; tick++) {
            clock.advance();
        }
        service.sweepCache();
        assertEquals(0, service.cacheSize());
        assertEquals("99", service.expandCached("%server_online%", PlaceholderContext.global("world")));
    }

    @Test
    void resolverReturningNullLeavesTextAlone() {
        registry.register("econ_balance", context -> null);
        assertEquals("%econ_balance%", service.expand("%econ_balance%", PlaceholderContext.global("world")));
    }
}
