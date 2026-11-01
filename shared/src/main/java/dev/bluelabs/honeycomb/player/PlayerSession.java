package dev.bluelabs.honeycomb.player;

import dev.bluelabs.honeycomb.display.PlayerBoard;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cached per-player display state.
 *
 * <p>Every display keeps the last value it sent to a player here. That is what
 * lets Honeycomb skip re-sending identical text, which is the main cause of
 * tablist and scoreboard flicker, and lets it clean up exactly what it created
 * when the player leaves.</p>
 */
public final class PlayerSession {

    private final UUID uniqueId;
    private final String name;
    private final long joinTick;
    private String lastKnownWorld;

    /** Last tablist sort order applied to this player. */
    public int sortOrder = Integer.MIN_VALUE;

    /** Tablist state. */
    public final Tablist tablist = new Tablist();
    /** Scoreboard state. */
    public final Sidebar sidebar = new Sidebar();
    /** Nametag team state. */
    public final Nametag nametag = new Nametag();
    /** Bossbar state. */
    public final Bossbars bossbars = new Bossbars();
    /** Below-name state. */
    public final BelowName belowName = new BelowName();

    /**
     * @param player   the player
     * @param joinTick tick the player joined, for diagnostics
     */
    public PlayerSession(Player player, long joinTick) {
        this.uniqueId = player.getUniqueId();
        this.name = player.getName();
        this.joinTick = joinTick;
        this.lastKnownWorld = player.getWorld() == null ? "" : player.getWorld().getName();
    }

    /**
     * @return the player's unique id
     */
    public UUID uniqueId() {
        return uniqueId;
    }

    /**
     * @return the player's name at join time
     */
    public String name() {
        return name;
    }

    /**
     * @return the tick this session was created
     */
    public long joinTick() {
        return joinTick;
    }

    /**
     * @return the last world this player was seen in
     */
    public String world() {
        return lastKnownWorld;
    }

    /**
     * @param worldName the world the player moved to
     */
    public void world(String worldName) {
        this.lastKnownWorld = worldName == null ? "" : worldName;
    }

    /**
     * @return a stable non-negative offset used to spread updates across ticks
     */
    public int updateOffset() {
        return Math.floorMod(uniqueId.hashCode(), 1000);
    }

    /** Tablist header, footer and player list name. */
    public static final class Tablist {
        /** Active profile name. */
        public String profile;
        /** Last header sent. */
        public Component header;
        /** Last footer sent. */
        public Component footer;
        /** Last player list name sent. */
        public Component listName;

        /**
         * Forgets everything that was sent, so the next tick re-applies it.
         */
        public void reset() {
            profile = null;
            header = null;
            footer = null;
            listName = null;
        }
    }

    /** Sidebar scoreboard. */
    public static final class Sidebar {
        /** Active profile name. */
        public String profile;
        /** Private board installed on the player, {@code null} when unused. */
        public PlayerBoard board;
        /** True when the current sidebar hides score numbers. */
        public boolean hideNumbers;

        /**
         * Forgets the applied layout so the next tick rewrites it.
         */
        public void reset() {
            profile = null;
        }
    }

    /** Nametag team. */
    public static final class Nametag {
        /** Team name Honeycomb created for this player. */
        public String team;
        /** Prefix currently applied. */
        public Component prefix;
        /** Suffix currently applied. */
        public Component suffix;
        /** Group key the decoration was resolved for. */
        public String groupKey;
        /** Decoration version applied per target player on this viewer's board. */
        public final Map<UUID, Long> appliedVersions = new HashMap<>();
        /** Board the versions were applied to, so a new board forces a re-apply. */
        public org.bukkit.scoreboard.Scoreboard lastBoard;

        /**
         * Forgets the applied decoration.
         */
        public void reset() {
            team = null;
            prefix = null;
            suffix = null;
            groupKey = null;
            appliedVersions.clear();
            lastBoard = null;
        }
    }

    /** Active bossbars for this player. */
    public static final class Bossbars {
        /** Bars currently shown, keyed by definition id. */
        public final Map<String, BossBar> shown = new HashMap<>();
        /** Tick at which each bar should be removed, keyed by definition id. */
        public final Map<String, Long> expiry = new HashMap<>();

        /**
         * Forgets every tracked bar. The caller is responsible for hiding them.
         */
        public void reset() {
            shown.clear();
            expiry.clear();
        }
    }

    /** Below-name display. */
    public static final class BelowName {
        /** Active profile name. */
        public String profile;
        /** True when the active profile uses Minecraft's health criteria. */
        public boolean healthMode;
        /** Last text rendered per target player, used to skip identical updates. */
        public final Map<UUID, Component> lastValues = new HashMap<>();

        /**
         * Forgets the applied values so the next tick rewrites them.
         */
        public void reset() {
            profile = null;
            lastValues.clear();
        }
    }
}
