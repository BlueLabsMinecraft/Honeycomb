package dev.bluelabs.honeycomb.api;

import dev.bluelabs.honeycomb.animation.Animation;
import dev.bluelabs.honeycomb.placeholder.PlaceholderResolver;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Public Honeycomb API.
 *
 * <p>Obtain it with {@link HoneycombApiProvider#get()} after Honeycomb has
 * enabled. Every method is safe to call from the main thread; display changes are
 * applied immediately and Honeycomb's own update loop keeps them in sync.</p>
 */
public interface HoneycombApi {

    /**
     * @return the plugin version, for example {@code 1.0.0}
     */
    String version();

    /**
     * @return the Minecraft version ranges this build supports
     */
    List<String> supportedMinecraftVersions();

    /**
     * Registers a placeholder that can be used as {@code %id%} in any template.
     *
     * @param id       placeholder id without percent signs
     * @param resolver resolver, may return {@code null} to leave the text untouched
     */
    void registerPlaceholder(String id, PlaceholderResolver resolver);

    /**
     * Removes a placeholder registered through the API.
     *
     * @param id placeholder id
     * @return true when a placeholder was removed
     */
    boolean unregisterPlaceholder(String id);

    /**
     * Registers an animation usable as {@code <anim:id>}.
     *
     * @param animation the animation
     */
    void registerAnimation(Animation animation);

    /**
     * Removes an animation.
     *
     * @param id animation id
     * @return true when an animation was removed
     */
    boolean unregisterAnimation(String id);

    /**
     * Registers a nametag decoration. Groups with a permission are only applied to
     * players holding it; higher priority wins.
     *
     * @param name       group key
     * @param priority   selection priority
     * @param prefix     prefix template, MiniMessage
     * @param suffix     suffix template, MiniMessage
     * @param permission permission that grants the group, may be {@code null}
     */
    void registerNametagFormat(String name, int priority, String prefix, String suffix, String permission);

    /**
     * Removes a nametag decoration registered through the API.
     *
     * @param name group key
     * @return true when a group was removed
     */
    boolean unregisterNametagFormat(String name);

    /**
     * Renders a template for a player: animations, placeholders and MiniMessage.
     *
     * @param player   the player
     * @param template the template
     * @return the rendered component
     */
    Component render(Player player, String template);

    /**
     * Sets a player's tablist header until Honeycomb's next update.
     *
     * @param player the player
     * @param header the header
     */
    void setTablistHeader(Player player, Component header);

    /**
     * Sets a player's tablist footer until Honeycomb's next update.
     *
     * @param player the player
     * @param footer the footer
     */
    void setTablistFooter(Player player, Component footer);

    /**
     * Shows a bossbar to one player. Honeycomb does not track or remove bars
     * created this way; the caller owns the returned instance.
     *
     * @param player   the player
     * @param title    the title
     * @param progress progress between 0 and 1
     * @param color    bar colour
     * @param overlay  bar style
     * @return the created bar
     */
    BossBar showBossbar(Player player, Component title, float progress, BossBar.Color color, BossBar.Overlay overlay);

    /**
     * Hides a bossbar created with {@link #showBossbar}.
     *
     * @param player the player
     * @param bar    the bar
     */
    void hideBossbar(Player player, BossBar bar);

    /**
     * Re-applies every display to one player.
     *
     * @param player the player
     */
    void refresh(Player player);

    /**
     * Re-applies every display to every online player.
     */
    void refreshAll();
}
