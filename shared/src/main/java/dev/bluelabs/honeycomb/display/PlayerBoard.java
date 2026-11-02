package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.display.scoreboard.PlayerSidebar;
import dev.bluelabs.honeycomb.display.scoreboard.Sidebar;
import dev.bluelabs.honeycomb.display.scoreboard.packet.PacketSidebar;
import org.bukkit.entity.Player;
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
    private Sidebar sidebar;
    private Objective belowNameObjective;

    private PlayerBoard(Scoreboard board) {
        this.board = board;
        this.owner = null;
    }

    /**
     * Creates a fresh private board.
     *
     * @return the board
     */
    public static PlayerBoard create(Player player) {
        try {
            return new PlayerBoard(Bukkit.getScoreboardManager().getNewScoreboard());
        } catch (RuntimeException | LinkageError unavailable) {
            // Folia has no private scoreboards: the manager throws. Packets do not need
            // one, so the board is created without a scoreboard and draws over the wire.
            if (PacketSidebar.available()) {
                if (!REPORTED_PACKETS) {
                    REPORTED_PACKETS = true;
                    Bukkit.getLogger().info("[Honeycomb] No private scoreboards on this server ("
                            + unavailable.getClass().getSimpleName()
                            + "), so the scoreboard is sent as packets.");
                }
                return new PlayerBoard(player);
            }
            if (!REPORTED) {
                REPORTED = true;
                Bukkit.getLogger().warning("[Honeycomb] This server cannot create a private scoreboard ("
                        + unavailable.getClass().getSimpleName() + ") and packets are unavailable ("
                        + PacketSidebar.unavailableReason() + "), so the scoreboard and below-name "
                        + "displays are off. Tablist, nametags, bossbars and holograms are unaffected.");
            }
            return null;
        }
    }

    private PlayerBoard(Player player) {
        this.board = null;
        this.owner = player;
    }

    private static boolean REPORTED;
    private static boolean REPORTED_PACKETS;
    private final Player owner;

    /**
     * Returns the sidebar, creating it when needed.
     *
     * @param hideNumbers whether score numbers should be hidden
     * @return the sidebar
     */
    public Sidebar sidebar(boolean hideNumbers) {
        if (sidebar == null) {
            sidebar = board != null
                    ? new PlayerSidebar(board, hideNumbers)
                    : packetSidebar(hideNumbers);
        }
        return sidebar;
    }

    private Sidebar packetSidebar(boolean hideNumbers) {
        try {
            return new PacketSidebar(owner, hideNumbers);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!REPORTED_PACKETS) {
                REPORTED_PACKETS = true;
                Bukkit.getLogger().warning("[Honeycomb] The packet scoreboard could not start: "
                        + failure.getClass().getName() + ": " + failure.getMessage());
            }
            return new Sidebar() {
                @Override
                public void update(net.kyori.adventure.text.Component title,
                                   java.util.List<net.kyori.adventure.text.Component> lines) {
                    // Nothing to draw on.
                }

                @Override
                public void dispose() {
                    // Nothing to remove.
                }
            };
        }
    }

    /**
     * Returns the below-name objective, creating it when needed.
     *
     * @param healthBased true to use Minecraft's built-in health criteria
     * @return the objective, already shown in the below-name slot
     */
    public Objective belowNameObjective(boolean healthBased) {
        if (board == null) {
            return null;
        }
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
        if (board == null) {
            return;
        }
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
    public Sidebar sidebarOrNull() {
        return sidebar;
    }

    /**
     * @return the below-name objective, or {@code null} when it was never created
     */
    public Objective belowNameObjectiveOrNull() {
        return belowNameObjective;
    }
}
