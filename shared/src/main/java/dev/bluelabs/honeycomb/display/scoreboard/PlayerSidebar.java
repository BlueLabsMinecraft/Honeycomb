package dev.bluelabs.honeycomb.display.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar Honeycomb owns for one player.
 *
 * <p>Rows are real scoreboard scores with a custom name, not team prefixes. That
 * means each row can hold a full component (gradients, hover events, any length)
 * and two identical lines never collide. Score values are fixed per row index, so
 * inserting or removing a row does not renumber the others, which is what keeps
 * the sidebar free of the usual flicker.</p>
 */
public final class PlayerSidebar {

    private static final String OBJECTIVE_NAME = "honeycomb";
    private static final int HIGHEST_SCORE = ScoreboardProfile.MAX_LINES;

    private final Scoreboard board;
    private final Objective objective;
    private final List<String> entries = new ArrayList<>();
    private final List<Component> rendered = new ArrayList<>();
    private Component title;
    private boolean hiddenNumbersApplied;

    /**
     * Attaches a sidebar objective to an existing board.
     *
     * @param board       the board the sidebar lives on
     * @param hideNumbers whether score numbers are hidden
     */
    public PlayerSidebar(Scoreboard board, boolean hideNumbers) {
        this.board = board;
        this.objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.hiddenNumbersApplied = hideNumbers;
    }

    /**
     * Replaces the visible rows, updating only what changed.
     *
     * @param newTitle the title component
     * @param newLines the rendered rows, top to bottom
     */
    public void update(Component newTitle, List<Component> newLines) {
        if (!newTitle.equals(title)) {
            objective.displayName(newTitle);
            title = newTitle;
        }

        int wanted = Math.min(newLines.size(), ScoreboardProfile.MAX_LINES);
        // Rows beyond the new size are removed first so the client never shows leftovers.
        for (int index = wanted; index < entries.size(); index++) {
            board.resetScores(entries.get(index));
        }
        while (entries.size() > wanted) {
            entries.remove(entries.size() - 1);
            rendered.remove(rendered.size() - 1);
        }

        for (int index = 0; index < wanted; index++) {
            Component line = newLines.get(index);
            if (index >= entries.size()) {
                String entry = entryFor(index);
                Score score = objective.getScore(entry);
                // Fixed score per row: stable ordering without renumbering other rows.
                score.setScore(HIGHEST_SCORE - index);
                if (hiddenNumbersApplied) {
                    score.numberFormat(NumberFormat.blank());
                }
                score.customName(line);
                entries.add(entry);
                rendered.add(line);
                continue;
            }
            if (!line.equals(rendered.get(index))) {
                objective.getScore(entries.get(index)).customName(line);
                rendered.set(index, line);
            }
        }
    }

    /**
     * Removes the objective from the board.
     */
    public void dispose() {
        try {
            objective.unregister();
        } catch (IllegalStateException ignored) {
            // Already unregistered by another plugin or by the server on quit.
        }
        entries.clear();
        rendered.clear();
    }

    /**
     * @return the scoreboard holding this sidebar
     */
    public Scoreboard board() {
        return board;
    }

    /**
     * @return number of visible rows
     */
    public int lineCount() {
        return entries.size();
    }

    /**
     * Builds a unique, invisible scoreboard entry for a row index.
     *
     * @param index row index
     * @return the entry string
     */
    static String entryFor(int index) {
        char digit = Character.forDigit(index % 16, 16);
        String suffix = index < 16 ? "" : Integer.toHexString(index / 16);
        return "\u00A7" + digit + suffix + "\u00A7r";
    }
}
