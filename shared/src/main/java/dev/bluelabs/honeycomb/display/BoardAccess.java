package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.player.PlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hands out and releases the single private board a player may have.
 *
 * <p>Several features share one board, so creation and teardown has to be
 * coordinated: a board is only removed once no feature is using it any more.</p>
 */
public final class BoardAccess {

    private BoardAccess() {
    }

    /**
     * Returns the player's Honeycomb board, creating and installing it when
     * needed.
     *
     * @param player  the player
     * @param session the player's session
     * @return the board
     */
    public static PlayerBoard acquire(Player player, PlayerSession session) {
        PlayerBoard board = session.sidebar.board;
        if (board == null) {
            board = PlayerBoard.create(player);
            if (board == null) {
                return null;
            }
            session.sidebar.board = board;
        }
        if (board.board() != null && player.getScoreboard() != board.board()) {
            // Another plugin may have replaced the board since the last update.
            player.setScoreboard(board.board());
        }
        return board;
    }

    /**
     * Removes the board when nothing uses it any more and gives the player the
     * main scoreboard back.
     *
     * @param player  the player
     * @param session the player's session
     */
    public static void releaseIfUnused(Player player, PlayerSession session) {
        if (session.sidebar.profile != null || session.belowName.profile != null) {
            return;
        }
        PlayerBoard board = session.sidebar.board;
        if (board == null) {
            return;
        }
        board.dispose();
        session.sidebar.board = null;
        if (board.board() != null && player.getScoreboard() == board.board()) {
            try {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            } catch (RuntimeException | LinkageError noMainBoard) {
                // Folia keeps the player on the board it already has.
            }
        }
    }
}
