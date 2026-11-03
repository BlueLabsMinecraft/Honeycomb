package dev.bluelabs.honeycomb.welcome;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.MatchContext;
import dev.bluelabs.honeycomb.display.ProfileConditions;
import dev.bluelabs.honeycomb.display.ProfileSelector;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welcome and leave messages, one entry per audience.
 *
 * <p>An entry says who it is for (private to the player, or broadcast to everyone),
 * whether it fires on join or on leave, and the conditions that pick the audience.
 * Entries are matched with the same conditions and priority rules as the displays,
 * so a staff greeting can outrank the default one.</p>
 */
public final class WelcomeService implements Listener {

    /** One configured message with its audience and trigger. */
    private static final class Entry extends DisplayProfile {

        private final String scope;
        private final String trigger;
        private final List<String> lines;

        private Entry(String name, int priority, boolean enabled, ProfileConditions conditions,
                      String scope, String trigger, List<String> lines) {
            super(name, priority, enabled, conditions);
            this.scope = scope;
            this.trigger = trigger;
            this.lines = lines;
        }

        private boolean broadcast() {
            return "broadcast".equals(scope);
        }

        private boolean onLeave() {
            return "leave".equals(trigger);
        }
    }

    private final HoneycombPlugin plugin;
    private final RenderPipeline pipeline;
    private List<Entry> entries = List.of();

    /**
     * @param plugin   plugin instance
     * @param pipeline render pipeline, so placeholders and animations work here too
     */
    public WelcomeService(HoneycombPlugin plugin, RenderPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
        reload();
    }

    /** Re-reads welcome.yml. */
    public void reload() {
        var yaml = plugin.configManager().yaml(ConfigType.WELCOME);
        ConfigurationSection root = yaml.getConfigurationSection("welcome");
        List<Entry> parsed = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                List<String> lines = section.getStringList("lines");
                parsed.add(new Entry(id,
                        section.getInt("priority", 0),
                        section.getBoolean("enabled", true),
                        ProfileConditions.parse(section, "welcome.yml", plugin.report()),
                        section.getString("scope", "private").toLowerCase(Locale.ROOT),
                        section.getString("trigger", "join").toLowerCase(Locale.ROOT),
                        lines));
            }
        }
        entries = parsed;
    }

    /**
     * Sends the welcome side of the messages.
     *
     * @param event join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Entry entry = select(player, false);
        if (entry == null) {
            return;
        }
        if (entry.broadcast()) {
            event.joinMessage(pipeline.renderMultiline(entry.lines, PlaceholderContext.of(player)));
            return;
        }
        // A tick later: the player is still in the loading screen when this fires.
        dev.bluelabs.honeycomb.scheduler.Scheduling.forPlayerLater(plugin, player, 2L,
                () -> player.sendMessage(pipeline.renderMultiline(entry.lines, PlaceholderContext.of(player))));
    }

    /**
     * Sends the leave side of the messages.
     *
     * @param event quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Entry entry = select(player, true);
        if (entry == null) {
            return;
        }
        if (entry.broadcast()) {
            event.quitMessage(pipeline.renderMultiline(entry.lines, PlaceholderContext.of(player)));
        } else {
            player.sendMessage(pipeline.renderMultiline(entry.lines, PlaceholderContext.of(player)));
        }
    }

    private Entry select(Player player, boolean leave) {
        String group = plugin.integrations().groups().group(player);
        MatchContext context = MatchContext.of(player, group)
                .withPlaceholders(text -> pipeline.prepare(text, PlaceholderContext.of(player)));
        List<Entry> candidates = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isEnabled() && entry.onLeave() == leave) {
                candidates.add(entry);
            }
        }
        return ProfileSelector.select(candidates, context);
    }

    /**
     * @return how many entries are configured, for the panel
     */
    public int size() {
        return entries.size();
    }
}
