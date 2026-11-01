package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.display.scoreboard.PlayerSidebar;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The scoreboard Honeycomb installs on a player.
 *
 * <p>A player can only display one scoreboard, so every Honeycomb scoreboard
 * feature (sidebar, below-name) shares this object instead of fighting over the
 * player's view. It is always a private board, never the server main scoreboard,
 * which is what keeps other plugins' teams untouched.</p>
 */
public final class PlayerBoard {

    private static final String BELOW_NAME_OBJECTIVE = "hcbname";

    private final Scoreboard board;
    private PlayerSidebar sidebar;
    private Objective belowNameObjective;

    private PlayerBoard(Scoreboard board) {
        this.board = board;
    }

    /**
     * Creates a fresh private board.
     *
     * @return the board
     */
    public static PlayerBoard create() {
        return new PlayerBoard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    /**
     * Returns the sidebar, creating it when needed.
     *
     * @param hideNumbers whether score numbers should be hidden
     * @return the sidebar
     */
    public PlayerSidebar sidebar(boolean hideNumbers) {
        if (sidebar == null) {
            sidebar = new PlayerSidebar(board, hideNumbers);
        }
        return sidebar;
    }

    /**
     * Returns the below-name objective, creating it when needed.
     *
     * @param healthBased true to use Minecraft's built-in health criteria
     * @return the objective, already shown in the below-name slot
     */
    public Objective belowNameObjective(boolean healthBased) {
        if (belowNameObjective == null) {
            Criteria criteria = healthBased ? Criteria.HEALTH : Criteria.DUMMY;
            belowNameObjective = board.registerNewObjective(BELOW_NAME_OBJECTIVE, criteria,
                    net.kyori.adventure.text.Component.empty());
            belowNameObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        return belowNameObjective;
    }

    /**
     * Removes the below-name objective.
     */
    public void disposeBelowName() {
        if (belowNameObjective != null) {
            try {
                belowNameObjective.unregister();
            } catch (IllegalStateException ignored) {
                // Already gone: the board was replaced or the player left.
            }
            belowNameObjective = null;
        }
    }

    /**
     * Removes the sidebar objective.
     */
    public void disposeSidebar() {
        if (sidebar != null) {
            sidebar.dispose();
            sidebar = null;
        }
    }

    /**
     * Removes everything Honeycomb placed on this board.
     */
    public void dispose() {
        disposeSidebar();
        disposeBelowName();
        for (Objective objective : board.getObjectives()) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
                // Nothing left to do.
            }
        }
    }

    /**
     * @return the underlying scoreboard
     */
    public Scoreboard board() {
        return board;
    }

    /**
     * @return the sidebar, or {@code null} when it was never created
     */
    public PlayerSidebar sidebarOrNull() {
        return sidebar;
    }

    /**
     * @return the below-name objective, or {@code null} when it was never created
     */
    public Objective belowNameObjectiveOrNull() {
        return belowNameObjective;
    }
}
