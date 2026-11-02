package dev.bluelabs.honeycomb.display.actionbar;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Sends the line above the hotbar.
 *
 * <p>The text is a template, so it can carry a balance, a rank or an animation. It
 * is only sent when the plugin is switched on in config.yml, and it is resent on
 * the configured interval instead of every tick, which is what keeps it cheap on
 * a busy server.</p>
 */
public final class ActionbarService implements DisplaySystem {

    private final ConfigManager configManager;
    private final RenderPipeline pipeline;

    private boolean enabled;
    private int interval = 40;
    private String template = "";
    private final java.util.List<Variant> variants = new java.util.ArrayList<>();

    /** One named action bar text with the conditions that select it. */
    private record Variant(String id, String template, int priority,
                           java.util.List<String> worlds, java.util.List<String> permissions) {
    }

    /**
     * @param configManager configuration manager
     * @param pipeline      render pipeline
     */
    public ActionbarService(ConfigManager configManager, RenderPipeline pipeline) {
        this.configManager = configManager;
        this.pipeline = pipeline;
        read();
    }

    @Override
    public String name() {
        return "actionbar";
    }

    @Override
    public void enable() {
        read();
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public void reload() {
        read();
    }

    @Override
    public boolean isEnabled() {
        return enabled && !template.isBlank();
    }

    @Override
    public int updateInterval() {
        return interval;
    }

    @Override
    public void tick(Player player) {
        if (!enabled) {
            return;
        }
        String text = variantFor(player);
        if (text.isBlank()) {
            return;
        }
        player.sendActionBar(pipeline.render(text, PlaceholderContext.of(player)));
    }

    /**
     * Picks the highest priority named text whose conditions pass, falling back to
     * the plain {@code actionbar.template}.
     *
     * @param player the player
     * @return the template to render
     */
    private String variantFor(Player player) {
        Variant best = null;
        for (Variant variant : variants) {
            if (!matches(variant, player)) {
                continue;
            }
            if (best == null || variant.priority() > best.priority()) {
                best = variant;
            }
        }
        return best == null ? template : best.template();
    }

    private boolean matches(Variant variant, Player player) {
        String world = player.getWorld().getName();
        if (!variant.worlds().isEmpty()) {
            boolean any = false;
            for (String entry : variant.worlds()) {
                boolean negated = entry.startsWith("!");
                String pattern = (negated ? entry.substring(1) : entry).replace("*", "");
                boolean hit = entry.contains("*")
                        ? world.startsWith(pattern) || world.contains(pattern)
                        : world.equals(pattern);
                if (negated && hit) {
                    return false;
                }
                if (!negated && hit) {
                    any = true;
                }
            }
            if (!any) {
                return false;
            }
        }
        for (String permission : variant.permissions()) {
            if (!player.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void refresh(Player player) {
        tick(player);
    }

    @Override
    public void forget(Player player) {
        // Nothing is stored per player: the client clears the bar on its own.
    }

    @Override
    public String describe() {
        return isEnabled() ? "actionbar on, every " + interval + " ticks" : "actionbar off";
    }

    private void read() {
        YamlConfiguration yaml = configManager.yaml(ConfigType.CONFIG);
        enabled = yaml.getBoolean("actionbar.enabled", false);
        interval = Math.max(1, yaml.getInt("actionbar.update-interval", 40));
        template = yaml.getString("actionbar.template", "");

        variants.clear();
        org.bukkit.configuration.ConfigurationSection profiles =
                yaml.getConfigurationSection("actionbar.profiles");
        if (profiles != null) {
            for (String id : profiles.getKeys(false)) {
                String base = "actionbar.profiles." + id;
                if (!yaml.getBoolean(base + ".enabled", true)) {
                    continue;
                }
                String text = yaml.getString(base + ".template", "");
                if (text.isBlank()) {
                    continue;
                }
                variants.add(new Variant(id, text, yaml.getInt(base + ".priority", 0),
                        yaml.getStringList(base + ".conditions.worlds"),
                        yaml.getStringList(base + ".conditions.permissions")));
            }
        }
    }
}
