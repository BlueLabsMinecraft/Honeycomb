package dev.bluelabs.honeycomb.sorting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordering rules for the tablist.
 *
 * <p>Groups are ordered by an explicit weight when one is configured, otherwise by
 * their position in the {@code groups} list. Unknown groups always sort below
 * known ones so a new group cannot silently jump to the top.</p>
 */
public final class SortingRule {

    private final List<String> groupOrder;
    private final Map<String, Integer> weights;
    private final boolean alphabeticalWithinGroup;
    private final boolean staffFirst;

    /**
     * @param groupOrder              group names, highest first
     * @param weights                 explicit weights per group, may be empty
     * @param alphabeticalWithinGroup sort players of one group by name
     * @param staffFirst              place players with {@code honeycomb.staff} first
     */
    public SortingRule(List<String> groupOrder, Map<String, Integer> weights,
                       boolean alphabeticalWithinGroup, boolean staffFirst) {
        this.groupOrder = List.copyOf(groupOrder);
        this.weights = Map.copyOf(weights);
        this.alphabeticalWithinGroup = alphabeticalWithinGroup;
        this.staffFirst = staffFirst;
    }

    /**
     * @return an empty rule that preserves vanilla ordering
     */
    public static SortingRule empty() {
        return new SortingRule(List.of(), Map.of(), true, false);
    }

    /**
     * @return the configured group order
     */
    public List<String> groupOrder() {
        return groupOrder;
    }

    /**
     * @return true when names are compared inside a group
     */
    public boolean alphabeticalWithinGroup() {
        return alphabeticalWithinGroup;
    }

    /**
     * @return true when staff are pulled to the top
     */
    public boolean staffFirst() {
        return staffFirst;
    }

    /**
     * Computes a numeric rank for a group; higher ranks sort first.
     *
     * @param group the player's group, may be {@code null}
     * @return the rank
     */
    public int rankFor(String group) {
        String key = group == null ? "" : group.toLowerCase(Locale.ROOT);
        Integer explicit = weights.get(key);
        if (explicit != null) {
            return explicit;
        }
        int index = groupOrder.indexOf(key);
        if (index < 0) {
            return Integer.MIN_VALUE / 2;
        }
        // Earlier entries in the list must produce higher ranks.
        return (groupOrder.size() - index) * 100;
    }

    /**
     * Reads the rule from configuration values.
     *
     * @param order     group order, highest first
     * @param weights   explicit weights per group
     * @param alpha     alphabetical sorting inside a group
     * @param staffFirst pull staff to the top
     * @return the rule
     */
    public static SortingRule of(List<String> order, Map<String, Integer> weights,
                                 boolean alpha, boolean staffFirst) {
        List<String> normalised = new ArrayList<>(order.size());
        for (String entry : order) {
            if (entry != null && !entry.isBlank()) {
                normalised.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return new SortingRule(normalised, weights, alpha, staffFirst);
    }
}
