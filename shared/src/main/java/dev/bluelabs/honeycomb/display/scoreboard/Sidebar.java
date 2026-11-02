package dev.bluelabs.honeycomb.display.scoreboard;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Anything Honeycomb can draw a scoreboard on.
 *
 * <p>Two implementations exist because servers differ: {@link PlayerSidebar} uses the
 * Bukkit scoreboard API on Paper, and {@code PacketSidebar} writes the same thing with
 * packets on servers that have no private scoreboards, which is Folia.</p>
 */
public interface Sidebar {

    /**
     * Replaces the visible rows.
     *
     * @param title the title
     * @param lines the rows, top to bottom
     */
    void update(Component title, List<Component> lines);

    /**
     * Removes the scoreboard from the player's screen.
     */
    void dispose();
}
