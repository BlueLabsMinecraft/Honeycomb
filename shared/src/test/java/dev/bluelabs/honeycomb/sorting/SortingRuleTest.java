package dev.bluelabs.honeycomb.sorting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SortingRuleTest {

    private final SortingRule rule = SortingRule.of(
            List.of("owner", "admin", "default"), Map.of("vip", 500), true, false);

    @Test
    void listPositionDecidesRank() {
        assertTrue(rule.rankFor("owner") > rule.rankFor("admin"));
        assertTrue(rule.rankFor("admin") > rule.rankFor("default"));
    }

    @Test
    void explicitWeightOverridesPosition() {
        assertTrue(rule.rankFor("vip") > rule.rankFor("owner"));
    }

    @Test
    void unknownGroupsSortLast() {
        assertTrue(rule.rankFor("default") > rule.rankFor("some_new_group"));
        assertTrue(rule.rankFor(null) < rule.rankFor("default"));
    }

    @Test
    void higherRankComesFirst() {
        SortKey owner = new SortKey(UUID.randomUUID(), "Zoe", rule.rankFor("owner"), false);
        SortKey member = new SortKey(UUID.randomUUID(), "Adam", rule.rankFor("default"), false);
        assertTrue(owner.compare(member, true, false) < 0);
    }

    @Test
    void alphabeticalBreaksTies() {
        SortKey adam = new SortKey(UUID.randomUUID(), "Adam", 100, false);
        SortKey zoe = new SortKey(UUID.randomUUID(), "Zoe", 100, false);
        assertTrue(adam.compare(zoe, true, false) < 0);
    }

    @Test
    void staffFirstOverridesGroup() {
        SortKey staff = new SortKey(UUID.randomUUID(), "Zoe", 0, true);
        SortKey owner = new SortKey(UUID.randomUUID(), "Adam", 1000, false);
        assertTrue(staff.compare(owner, true, true) < 0);
    }
}
