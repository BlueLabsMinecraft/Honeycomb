package dev.bluelabs.honeycomb.animation;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ValidationReport;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads animation definitions from {@code animations.yml} and keeps any
 * animations registered through the API.
 */
public final class AnimationRegistry {

    private final Map<String, Animation> animations = new LinkedHashMap<>();
    private final ConfigManager configManager;
    private final ValidationReport report;

    /**
     * @param configManager configuration manager
     * @param report        report that receives validation warnings
     */
    public AnimationRegistry(ConfigManager configManager, ValidationReport report) {
        this.configManager = configManager;
        this.report = report;
    }

    /**
     * Reads every animation from the configuration file. Animations registered
     * through the API are kept untouched.
     */
    public void loadConfigured() {
        animations.entrySet().removeIf(entry -> !entry.getValue().id().startsWith("api:"));
        ConfigurationSection root = configManager.yaml(ConfigType.ANIMATIONS).getConfigurationSection("animations");
        if (root == null) {
            report.warning("animations.yml", "No 'animations' section found");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                report.warning("animations.yml", "Entry '" + key + "' must be a section");
                continue;
            }
            List<String> frames = new ArrayList<>();
            for (Object frame : section.getList("frames", List.of())) {
                if (frame != null) {
                    frames.add(String.valueOf(frame));
                }
            }
            if (frames.isEmpty()) {
                report.warning("animations.yml", "Animation '" + key + "' has no frames and was skipped");
                continue;
            }
            int interval = section.getInt("interval", 5);
            if (interval < 1) {
                report.warning("animations.yml", "Animation '" + key + "' interval must be at least 1, using 5");
                interval = 5;
            }
            animations.put(key, new Animation(key, interval, frames, section.getBoolean("loop", true)));
        }
    }

    /**
     * Registers an animation supplied by another plugin.
     *
     * @param animation the animation
     */
    public void register(Animation animation) {
        animations.put("api:" + animation.id().toLowerCase(Locale.ROOT), animation);
    }

    /**
     * Removes an animation registered through the API.
     *
     * @param id animation id
     * @return true when something was removed
     */
    public boolean unregister(String id) {
        return animations.remove("api:" + id.toLowerCase(Locale.ROOT)) != null
                || animations.remove(id) != null;
    }

    /**
     * @param id animation id as written in a template
     * @return the animation, or {@code null} when it does not exist
     */
    public Animation get(String id) {
        if (id == null) {
            return null;
        }
        Animation animation = animations.get(id);
        if (animation != null) {
            return animation;
        }
        return animations.get("api:" + id.toLowerCase(Locale.ROOT));
    }

    /**
     * @param id   animation id
     * @param tick global tick
     * @return the raw frame template, or {@code null} when the animation is unknown
     */
    public String frameAt(String id, long tick) {
        Animation animation = get(id);
        return animation == null ? null : animation.frameAt(tick);
    }

    /**
     * @return every known animation id
     */
    public List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(animations.keySet()));
    }

    /**
     * @return number of known animations
     */
    public int size() {
        return animations.size();
    }
}
