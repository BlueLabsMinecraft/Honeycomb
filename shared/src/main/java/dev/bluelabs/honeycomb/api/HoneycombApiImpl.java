package dev.bluelabs.honeycomb.api;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.animation.Animation;
import dev.bluelabs.honeycomb.display.nametag.NametagGroup;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.placeholder.PlaceholderResolver;
import dev.bluelabs.honeycomb.util.MinecraftVersions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Default implementation, delegating to the plugin's services.
 */
public final class HoneycombApiImpl implements HoneycombApi {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public HoneycombApiImpl(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String version() {
        return plugin.version();
    }

    @Override
    public List<String> supportedMinecraftVersions() {
        return MinecraftVersions.testedRanges();
    }

    @Override
    public void registerPlaceholder(String id, PlaceholderResolver resolver) {
        plugin.placeholders().registry().register(id, resolver);
    }

    @Override
    public boolean unregisterPlaceholder(String id) {
        return plugin.placeholders().registry().unregister(id);
    }

    @Override
    public void registerAnimation(Animation animation) {
        plugin.animations().register(animation);
    }

    @Override
    public boolean unregisterAnimation(String id) {
        return plugin.animations().unregister(id);
    }

    @Override
    public void registerNametagFormat(String name, int priority, String prefix, String suffix, String permission) {
        plugin.nametags().registerGroup(NametagGroup.apiGroup(name, priority, prefix, suffix, permission));
    }

    @Override
    public boolean unregisterNametagFormat(String name) {
        return plugin.nametags().unregisterGroup(name);
    }

    @Override
    public Component render(Player player, String template) {
        return plugin.pipeline().render(template, PlaceholderContext.of(player));
    }

    @Override
    public void setTablistHeader(Player player, Component header) {
        player.sendPlayerListHeader(header);
    }

    @Override
    public void setTablistFooter(Player player, Component footer) {
        player.sendPlayerListFooter(footer);
    }

    @Override
    public BossBar showBossbar(Player player, Component title, float progress,
                               BossBar.Color color, BossBar.Overlay overlay) {
        BossBar bar = BossBar.bossBar(title, progress, color, overlay);
        player.showBossBar(bar);
        return bar;
    }

    @Override
    public void hideBossbar(Player player, BossBar bar) {
        player.hideBossBar(bar);
    }

    @Override
    public void refresh(Player player) {
        plugin.coordinator().refresh(player);
    }

    @Override
    public void refreshAll() {
        plugin.coordinator().refreshAll();
    }
}
