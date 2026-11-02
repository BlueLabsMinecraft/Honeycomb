package dev.bluelabs.honeycomb.display.scoreboard;

import dev.bluelabs.honeycomb.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * The scoreboard Honeycomb owns for one player.
 *
 * <p>Rows are real scores with a fixed value per row index, so inserting or removing
 * a row does not renumber the others and the board does not flicker. Row text is
 * written the modern way first, with a custom name per score. Servers that refuse
 * that (older builds, some Folia setups) fall back to a team prefix per row, which is
 * the same trick the long lived scoreboard libraries use: the entry stays invisible
 * and the visible text lives in the team.</p>
 */
public final class PlayerSidebar implements Sidebar {

    private static final String OBJECTIVE_NAME = "honeycomb";
    private static final String TEAM_PREFIX = "hcrow";
    private static final int HIGHEST_SCORE = ScoreboardProfile.MAX_LINES;

    private final Scoreboard board;
    private final Objective objective;
    private boolean hideNumbers;
    private final List<String> entries = new ArrayList<>();
    private final List<Component> rendered = new ArrayList<>();
    private Component title;
    private boolean hiddenNumbersApplied;
    private boolean plainEntries;
    private boolean teamMode;
    private boolean reportedTeamMode;

    /**
     * Attaches a scoreboard objective to an existing board.
     *
     * @param board       the board the scoreboard lives on
     * @param hideNumbers whether score numbers should be hidden
     */
    public PlayerSidebar(Scoreboard board, boolean hideNumbers) {
        this.board = board;
        Objective leftover = board.getObjective(OBJECTIVE_NAME);
        if (leftover != null) {
            // A board that survived a reload still owns the old objective.
            try {
                leftover.unregister();
            } catch (IllegalStateException alreadyGone) {
                // Nothing to clean up.
            }
        }
        this.objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.hiddenNumbersApplied = hideNumbers;
        this.hideNumbers = hideNumbers;
    }

    /**
     * Replaces the visible rows, updating only what changed.
     *
     * @param newTitle the title component
     * @param newLines the rendered rows, top to bottom
     */
    @Override
    public void update(Component newTitle, List<Component> newLines) {
        if (title == null) {
            // Reclaim the slot: another plugin may have taken it since we registered.
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        if (!newTitle.equals(title)) {
            objective.displayName(newTitle);
            title = newTitle;
        }

        int wanted = Math.min(newLines.size(), ScoreboardProfile.MAX_LINES);
        for (int index = wanted; index < entries.size(); index++) {
            dropRow(index);
        }
        while (entries.size() > wanted) {
            entries.remove(entries.size() - 1);
            rendered.remove(rendered.size() - 1);
        }

        for (int index = 0; index < wanted; index++) {
            Component line = newLines.get(index);
            if (index >= entries.size()) {
                createRow(index, line);
                continue;
            }
            if (!line.equals(rendered.get(index))) {
                write(index, line);
                rendered.set(index, line);
            }
        }
    }

    private void createRow(int index, Component line) {
        String entry = entryName(index);
        Score score = objective.getScore(entry);
        score.setScore(HIGHEST_SCORE - index);
        if (hiddenNumbersApplied) {
            hideNumber(score);
        }
        entries.add(entry);
        rendered.add(line);
        write(index, line);
    }

    private void dropRow(int index) {
        if (index >= entries.size()) {
            return;
        }
        String entry = entries.get(index);
        board.resetScores(entry);
        Team team = board.getTeam(TEAM_PREFIX + index);
        if (team != null) {
            team.unregister();
        }
    }

    /**
     * Writes a row. The modern custom name is tried first, because it accepts any
     * component. A server that refuses it is switched to team prefixes once, and
     * every visible row is rewritten through that path.
     *
     * @param index row index
     * @param line  rendered row
     */
    private void write(int index, Component line) {
        if (!teamMode) {
            try {
                objective.getScore(entries.get(index)).customName(line);
                return;
            } catch (UnsupportedOperationException | NoSuchMethodError unsupported) {
                teamMode = true;
                if (!reportedTeamMode) {
                    reportedTeamMode = true;
                    org.bukkit.Bukkit.getLogger().info("[Honeycomb] Scoreboard rows use teams on this "
                            + "server (" + unsupported.getClass().getSimpleName() + ").");
                }
                for (int row = 0; row < rendered.size(); row++) {
                    writePrefix(row, rendered.get(row));
                }
                writePrefix(index, line);
                return;
            }
        }
        writePrefix(index, line);
    }

    /**
     * Puts the row text on the row's team.
     *
     * @param index row index
     * @param line  rendered row
     */
    private void writePrefix(int index, Component line) {
        String name = TEAM_PREFIX + index;
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        String entry = entries.get(index);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        try {
            team.prefix(line);
        } catch (UnsupportedOperationException | NoSuchMethodError unsupported) {
            String legacy = Text.toLegacy(line);
            team.setPrefix(legacy.length() > 64 ? legacy.substring(0, 64) : legacy);
        }
    }

    /**
     * Picks the score entry for a row, falling back to a plain one when the server
     * refuses coloured entries.
     *
     * @param index row index
     * @return a unique entry name
     */
    private String entryName(int index) {
        if (plainEntries) {
            return Integer.toString(index);
        }
        String candidate = entryFor(index);
        try {
            objective.getScore(candidate);
            return candidate;
        } catch (RuntimeException refused) {
            plainEntries = true;
            org.bukkit.Bukkit.getLogger().info("[Honeycomb] Scoreboard rows use plain entries on this "
                    + "server (" + refused.getClass().getSimpleName() + ").");
            return Integer.toString(index);
        }
    }

    /**
     * Hides the row number when the server supports blank number formats.
     *
     * @param score row score
     */
    private void hideNumber(Score score) {
        try {
            score.numberFormat(NumberFormat.blank());
        } catch (UnsupportedOperationException | NoSuchMethodError unsupported) {
            hideNumbers = false;
        }
    }

    /**
     * Removes the objective and its teams from the board.
     */
    @Override
    public void dispose() {
        for (int index = 0; index < ScoreboardProfile.MAX_LINES; index++) {
            Team team = board.getTeam(TEAM_PREFIX + index);
            if (team != null) {
                team.unregister();
            }
        }
        try {
            objective.unregister();
        } catch (IllegalStateException alreadyGone) {
            // Nothing to clean up.
        }
    }

    /**
     * Row entries are invisible colour codes so two identical lines never collide.
     *
     * @param index row index
     * @return the entry name
     */
    static String entryFor(int index) {
        char digit = Character.forDigit(index % 16, 16);
        String suffix = index < 16 ? "" : Integer.toHexString(index / 16);
        return "\u00A7" + digit + suffix + "\u00A7r";
    }
}
