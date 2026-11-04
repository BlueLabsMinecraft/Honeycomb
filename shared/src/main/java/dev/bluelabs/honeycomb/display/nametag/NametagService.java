package dev.bluelabs.honeycomb.display.nametag;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.Settings;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.BoardAccess;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.display.PlayerBoard;
import dev.bluelabs.honeycomb.display.MatchContext;
import dev.bluelabs.honeycomb.display.WorldProfiles;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.integration.PlayerStateProvider;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies nametag prefixes and suffixes.
 *
 * <p>Prefixes are per-viewer: every viewer's scoreboard needs the team of every
 * other player, so decorations are versioned. When a player's decoration changes
 * its version is bumped and only viewers still holding an older version receive an
 * update, which keeps a hundred player server at a handful of writes per cycle
 * instead of a hundred.</p>
 */
public final class NametagService implements DisplaySystem {

    private static final String TEAM_PREFIX = "hc";

    private final ConfigManager configManager;
    private final Settings settings;
    private final RenderPipeline pipeline;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final PlayerStateProvider states;
    private final TickClock clock;
    private final ValidationReport report;

    private final java.util.Set<UUID> warnedGroups = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Decoration> decorations = new HashMap<>();
    private final Map<UUID, Long> lastComputed = new HashMap<>();
    private long versionCounter;
    private WorldProfiles worldProfiles;

    private List<NametagGroup> nametagGroups = new ArrayList<>();
    private final Map<String, NametagGroup> apiGroups = new HashMap<>();
    private boolean enabled;
    private boolean debug;
    private boolean seeFriendlyInvisibles;
    private int serviceInterval = 40;

    /**
     * @param configManager configuration manager
     * @param settings      general settings
     * @param pipeline      render pipeline
     * @param sessions      player sessions
     * @param groups        permission group source
     * @param states        vanish and AFK source
     * @param clock         shared tick clock
     * @param report        validation report
     */
    public NametagService(ConfigManager configManager,
                          Settings settings,
                          RenderPipeline pipeline,
                          PlayerSessionRegistry sessions,
                          GroupProvider groups,
                          PlayerStateProvider states,
                          TickClock clock,
                          ValidationReport report) {
        this.configManager = configManager;
        this.settings = settings;
        this.pipeline = pipeline;
        this.sessions = sessions;
        this.groups = groups;
        this.states = states;
        this.clock = clock;
        this.report = report;
    }

    /**
     * @param worldProfiles per-world overrides, may be {@code null}
     */
    public void setWorldProfiles(WorldProfiles worldProfiles) {
        this.worldProfiles = worldProfiles;
    }

    @Override
    public String name() {
        return "nametags";
    }

    @Override
    public void enable() {
        ConfigurationSection root = configManager.yaml(ConfigType.NAMETAGS).getConfigurationSection("nametags");
        this.enabled = root != null && root.getBoolean("enabled", true);
        this.decorations.clear();
        this.lastComputed.clear();
        if (!enabled) {
            this.nametagGroups = new ArrayList<>();
            return;
        }
        this.nametagGroups = NametagGroup.loadAll(root, report);
        this.serviceInterval = Math.max(1, root.getInt("update-interval", 40));
        this.seeFriendlyInvisibles = root.getBoolean("see-friendly-invisibles", false);
        this.debug = root.getBoolean("debug-apply", false);
    }

    @Override
    public void disable() {
        removeTeamsFromBoards();
        this.decorations.clear();
        this.lastComputed.clear();
        for (PlayerSession session : sessions.all()) {
            session.nametag.reset();
        }
        this.enabled = false;
    }

    @Override
    public void reload() {
        enable();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.peek(player);
            if (session != null) {
                session.nametag.appliedVersions.clear();
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int updateInterval() {
        return serviceInterval;
    }

    @Override
    public void tick(Player viewer) {
        if (!enabled || nametagGroups.isEmpty()) {
            return;
        }
        PlayerSession session = sessions.get(viewer);
        if (!clock.isDue(serviceInterval, session.updateOffset())) {
            return;
        }
        if (settings.isWorldDisabled(viewer.getWorld().getName())) {
            return;
        }
        if (worldProfiles != null && worldProfiles.isDisabled("nametags", viewer.getWorld().getName())) {
            return;
        }
        if (settings.respectBypassPermission() && viewer.hasPermission("honeycomb.bypass")) {
            return;
        }
        applyToViewer(viewer, session);
    }

    @Override
    public void refresh(Player viewer) {
        if (!enabled) {
            return;
        }
        PlayerSession session = sessions.get(viewer);
        session.nametag.appliedVersions.clear();
        applyToViewer(viewer, session);
    }

    private void applyToViewer(Player viewer, PlayerSession session) {
        PlayerBoard acquired = BoardAccess.acquire(viewer, session);
        Scoreboard board = acquired == null ? viewer.getScoreboard() : acquired.board();
        if (session.nametag.lastBoard != board) {
            // A new board sees none of the teams we applied before.
            session.nametag.appliedVersions.clear();
            session.nametag.lastBoard = board;
        }
        int touched = 0;
        int decorated = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (states.vanished(target)) {
                continue;
            }
            Decoration decoration = decorationFor(target);
            if (decoration == null) {
                continue;
            }
            decorated++;
            Long applied = session.nametag.appliedVersions.get(target.getUniqueId());
            if (applied != null && applied == decoration.version()) {
                continue;
            }
            applyTeam(board, target, decoration);
            session.nametag.appliedVersions.put(target.getUniqueId(), decoration.version());
            touched++;
        }
        if (debug && warnedGroups.add(viewer.getUniqueId())) {
            Bukkit.getLogger().info("Honeycomb nametags debug: viewer=" + viewer.getName()
                    + " groups=" + nametagGroups.size() + " decorated=" + decorated
                    + " teams=" + touched + " board=" + board.getClass().getSimpleName()
                    + " online=" + Bukkit.getOnlinePlayers().size());
        }
        if (touched == 0 && nametagGroups.isEmpty() && warnedGroups.add(viewer.getUniqueId())) {
            Bukkit.getLogger().warning("Honeycomb nametags: enabled, but nametags.yml has no groups, "
                    + "so nothing is drawn.");
        }
    }

    private void applyTeam(Scoreboard board, Player target, Decoration decoration) {
        String teamName = teamName(target);
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
        // Only push what actually changed: every setter sends a packet.
        if (!team.prefix().equals(decoration.prefix())) {
            team.prefix(decoration.prefix());
        }
        if (!team.suffix().equals(decoration.suffix())) {
            team.suffix(decoration.suffix());
        }
        if (team.canSeeFriendlyInvisibles() != seeFriendlyInvisibles) {
            team.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        }
    }

    private Decoration decorationFor(Player target) {
        UUID id = target.getUniqueId();
        long now = clock.tick();
        Long last = lastComputed.get(id);
        Decoration existing = decorations.get(id);
        if (existing != null && last != null && now - last < serviceInterval) {
            return existing;
        }
        MatchContext match = MatchContext.of(target, groups.group(target))
                .withPlaceholders(text -> pipeline.placeholders().expand(text, PlaceholderContext.of(target)));
        List<NametagGroup> allowed = new ArrayList<>();
        for (NametagGroup candidate : allGroups()) {
            if (candidate.matches(match)) {
                allowed.add(candidate);
            }
        }
        NametagGroup group = NametagGroup.select(allowed, groups.group(target), target::hasPermission);
        if (group == null) {
            if (warnedGroups.add(id)) {
                Bukkit.getLogger().warning("Honeycomb nametags: no group matched " + target.getName()
                        + ". Every group needs the player's LuckPerms group name, a 'permission:'"
                        + " node they hold, or the group must be called 'default'.");
            }
            return existing;
        }
        PlaceholderContext context = PlaceholderContext.of(target);
        Component prefix = pipeline.render(group.prefixTemplate(), context);
        Component suffix = pipeline.render(group.suffixTemplate(), context);
        lastComputed.put(id, now);
        if (existing != null && existing.prefix().equals(prefix) && existing.suffix().equals(suffix)) {
            return existing;
        }
        Decoration decoration = new Decoration(prefix, suffix, group.name(), ++versionCounter);
        decorations.put(id, decoration);
        return decoration;
    }


    /**
     * Describes what a player's name tag will look like, for {@code /honeycomb nametags}.
     *
     * <p>This answers the question a server owner actually asks when nothing appears:
     * which group was picked, and what text that group renders to.</p>
     *
     * @param target player to inspect
     * @return one readable line
     */
    public String describe(Player target) {
        if (!enabled) {
            return target.getName() + ": nametags are disabled in nametags.yml";
        }
        Decoration decoration = decorationFor(target);
        if (decoration == null) {
            return target.getName() + ": no group matched, so nothing is drawn. Give the player"
                    + " honeycomb.group.<name>, set their LuckPerms group, or keep a group called"
                    + " 'default'.";
        }
        String plainPrefix = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(decoration.prefix());
        return target.getName() + ": group '" + decoration.groupKey()
                + "', prefix '" + plainPrefix + "'"
                + (plainPrefix.isBlank() ? " (blank, so the name looks unchanged)" : "");
    }

    /**
     * Registers a decoration supplied by another plugin.
     *
     * @param group the group
     */
    public void registerGroup(NametagGroup group) {
        apiGroups.put(group.name().toLowerCase(java.util.Locale.ROOT), group);
        decorations.clear();
        for (PlayerSession session : sessions.all()) {
            session.nametag.appliedVersions.clear();
        }
    }

    /**
     * Removes a decoration registered through the API.
     *
     * @param name group name
     * @return true when a group was removed
     */
    public boolean unregisterGroup(String name) {
        boolean removed = apiGroups.remove(name.toLowerCase(java.util.Locale.ROOT)) != null;
        if (removed) {
            decorations.clear();
        }
        return removed;
    }

    private List<NametagGroup> allGroups() {
        if (apiGroups.isEmpty()) {
            return nametagGroups;
        }
        List<NametagGroup> combined = new ArrayList<>(nametagGroups.size() + apiGroups.size());
        combined.addAll(nametagGroups);
        combined.addAll(apiGroups.values());
        return combined;
    }

    /**
     * Removes a player's team from every board Honeycomb knows about.
     */
    private void removePlayersTeams(Player player) {
        String teamName = teamName(player);
        for (Scoreboard board : boardsInUse()) {
            Team team = board.getTeam(teamName);
            if (team == null) {
                continue;
            }
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    private void removeTeamsFromBoards() {
        List<Scoreboard> boards = boardsInUse();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String teamName = teamName(player);
            for (Scoreboard board : boards) {
                Team team = board.getTeam(teamName);
                if (team == null) {
                    continue;
                }
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
    }

    private List<Scoreboard> boardsInUse() {
        List<Scoreboard> boards = new ArrayList<>();
        try {
            if (Bukkit.getScoreboardManager() != null) {
                boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } catch (RuntimeException | LinkageError noMainBoard) {
            // Folia has no main scoreboard, only player boards exist there.
        }
        for (PlayerSession session : sessions.all()) {
            if (session.sidebar.board != null) {
                boards.add(session.sidebar.board.board());
            }
        }
        return boards;
    }

    @Override
    public void forget(Player player) {
        removePlayersTeams(player);
        decorations.remove(player.getUniqueId());
        lastComputed.remove(player.getUniqueId());
        for (PlayerSession session : sessions.all()) {
            session.nametag.appliedVersions.remove(player.getUniqueId());
        }
    }

    @Override
    public String describe() {
        return nametagGroups.size() + " group(s), interval " + serviceInterval + "t";
    }

    /**
     * Builds the scoreboard team name for a player.
     *
     * @param player the player
     * @return a stable team name of at most sixteen characters
     */
    static String teamName(Player player) {
        String hex = Long.toHexString(player.getUniqueId().getMostSignificantBits());
        return TEAM_PREFIX + hex.substring(0, Math.min(10, hex.length()));
    }

    private record Decoration(Component prefix, Component suffix, String groupKey, long version) {
    }
}
