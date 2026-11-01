package dev.bluelabs.honeycomb.display;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileSelectorTest {

    private static final class TestProfile extends DisplayProfile {
        TestProfile(String name, int priority, boolean enabled, ProfileConditions conditions) {
            super(name, priority, enabled, conditions);
        }
    }

    private MatchContext context(Set<String> permissions) {
        return MatchContext.of("world", "default", permissions::contains);
    }

    @Test
    void highestPriorityMatchingProfileWins() {
        List<TestProfile> profiles = List.of(
                new TestProfile("default", 0, true, ProfileConditions.none()),
                new TestProfile("staff", 50, true, ProfileConditions.none())
        );
        assertEquals("staff", ProfileSelector.select(profiles, context(Set.of())).name());
    }

    @Test
    void disabledProfilesAreIgnored() {
        List<TestProfile> profiles = List.of(
                new TestProfile("default", 0, true, ProfileConditions.none()),
                new TestProfile("off", 100, false, ProfileConditions.none())
        );
        assertEquals("default", ProfileSelector.select(profiles, context(Set.of())).name());
    }

    @Test
    void returnsNullWhenNothingMatches() {
        List<TestProfile> profiles = List.of(
                new TestProfile("first", 10, false, ProfileConditions.none()),
                new TestProfile("second", 0, false, ProfileConditions.none())
        );
        assertNull(ProfileSelector.select(profiles, context(Set.of())));
    }
}
