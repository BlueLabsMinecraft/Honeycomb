package dev.bluelabs.honeycomb.sorting;

import java.util.UUID;

/**
 * Pre-computed ordering values for one player.
 *
 * @param uniqueId  the player
 * @param name      the player's name
 * @param rank      group rank, higher sorts first
 * @param staff     whether the player has staff status
 */
public record SortKey(UUID uniqueId, String name, int rank, boolean staff) {

    /**
     * Compares two keys, highest first.
     *
     * @param other          the other key
     * @param alphabetical   whether names break ties inside a group
     * @param staffFirst     whether staff precede everyone else
     * @return comparison result
     */
    public int compare(SortKey other, boolean alphabetical, boolean staffFirst) {
        if (staffFirst && staff != other.staff) {
            return staff ? -1 : 1;
        }
        if (rank != other.rank) {
            return Integer.compare(other.rank, rank);
        }
        if (alphabetical) {
            return name.compareToIgnoreCase(other.name);
        }
        return 0;
    }
}
