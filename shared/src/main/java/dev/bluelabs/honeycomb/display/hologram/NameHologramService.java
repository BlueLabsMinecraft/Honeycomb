package dev.bluelabs.honeycomb.display.hologram;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a player's name on a text display floating above their head.
 *
 * <p>The text is a template, so a permissions prefix, a level or a health value can
 * sit above every player without touching the vanilla nametag. Each player owns one
 * display entity that follows them; the display is created on the thread that owns
 * the player, which keeps it legal on Folia and cheap on Paper.</p>
 */
public final class NameHologramService implements DisplaySystem {

    private static final String[] OPERATORS = {">=", "<=", "==", "!=", "contains", ">", "<"};

    private final ConfigManager configManager;
    private final RenderPipeline pipeline;

    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, String> renderedText = new HashMap<>();
    private final List<Profile> profiles = new ArrayList<>();

    private boolean enabled;
    private int interval = 4;
    private double offset = 0.35D;
    private String template = "<white>%player_name%";
    private float viewRange = 1.0F;
    private boolean shadowed = true;
    private boolean seeThrough;
    private String background = "transparent";
    private Display.Billboard billboard = Display.Billboard.CENTER;
    private float scale = 1.0F;
    private int lineWidth = 220;
    private int brightness = -1;

    /**
     * @param configManager configuration manager
     * @param pipeline      render pipeline
     */
    public NameHologramService(ConfigManager configManager, RenderPipeline pipeline) {
        this.configManager = configManager;
        this.pipeline = pipeline;
        read();
    }

    @Override
    public String name() {
        return "holograms";
    }

    @Override
    public void enable() {
        read();
        if (!enabled) {
            removeAll();
        }
    }

    @Override
    public void disable() {
        enabled = false;
        removeAll();
    }

    @Override
    public void reload() {
        read();
        removeAll();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
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
        apply(player);
    }

    @Override
    public void refresh(Player player) {
        if (!enabled) {
            remove(player.getUniqueId());
            return;
        }
        apply(player);
    }

    @Override
    public void forget(Player player) {
        remove(player.getUniqueId());
    }

    @Override
    public String describe() {
        return displays.size() + " name hologram(s)";
    }

    /** Re-reads holograms.yml. */
    private void read() {
        YamlConfiguration yaml = configManager.yaml(ConfigType.HOLOGRAMS);
        enabled = yaml.getBoolean("holograms.enabled", false);
        interval = Math.max(1, yaml.getInt("holograms.update-interval", 4));
        offset = clamp(yaml.getDouble("holograms.offset", 0.35D));
        template = yaml.getString("holograms.template", "<white>%player_name%");
        viewRange = (float) Math.max(0.1D, Math.min(1.0D, yaml.getDouble("holograms.view-range", 1.0D)));
        shadowed = yaml.getBoolean("holograms.shadow", true);
        seeThrough = yaml.getBoolean("holograms.see-through", false);
        background = yaml.getString("holograms.background", "transparent");
        billboard = billboard(yaml.getString("holograms.billboard", "CENTER"));
        scale = (float) Math.max(0.25D, Math.min(4.0D, yaml.getDouble("holograms.scale", 1.0D)));
        lineWidth = Math.max(20, yaml.getInt("holograms.line-width", 220));
        brightness = Math.max(-1, Math.min(15, yaml.getInt("holograms.brightness", -1)));

        profiles.clear();
        ConfigurationSection section = yaml.getConfigurationSection("holograms.profiles");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            profiles.add(new Profile(
                    id,
                    entry.getInt("priority", 0),
                    entry.isSet("offset") ? Double.valueOf(clamp(entry.getDouble("offset"))) : null,
                    entry.isSet("template") ? entry.getString("template") : null,
                    lower(entry.getStringList("worlds")),
                    entry.getStringList("permissions"),
                    entry.getStringList("placeholders"),
                    entry.getConfigurationSection("conditions")));
        }
        profiles.sort(Comparator.comparingInt(Profile::priority).reversed());
    }

    /**
     * Applies the template and position to one player.
     *
     * @param player player to draw above
     */
    private void apply(Player player) {
        PlaceholderContext context = PlaceholderContext.of(player);
        Profile chosen = select(player, context);

        double height = chosen == null || chosen.offset() == null ? offset : chosen.offset();
        String textTemplate = chosen == null || chosen.template() == null ? template : chosen.template();

        Location target = player.getLocation().add(0.0D, player.getHeight() + height, 0.0D);
        UUID id = player.getUniqueId();
        TextDisplay display = displays.get(id);

        if (display == null || !display.isValid() || display.isDead()
                || !display.getWorld().equals(player.getWorld())) {
            if (display != null) {
                display.remove();
            }
            display = spawn(player, target, textTemplate, context);
            displays.put(id, display);
            renderedText.put(id, pipeline.prepare(textTemplate, context));
            return;
        }

        if (display.getLocation().distanceSquared(target) > 0.0004D) {
            display.teleport(target);
        }
        String ready = pipeline.prepare(textTemplate, context);
        if (!ready.equals(renderedText.get(id))) {
            display.text(pipeline.render(textTemplate, context));
            renderedText.put(id, ready);
        }
    }


    /**
     * @param name billboard setting as written in holograms.yml
     * @return the matching billboard, CENTER when the name is unknown
     */
    private static Display.Billboard billboard(String name) {
        String clean = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        for (Display.Billboard candidate : Display.Billboard.values()) {
            if (candidate.name().equals(clean)) {
                return candidate;
            }
        }
        return Display.Billboard.CENTER;
    }

    /**
     * Creates the display entity for a player.
     *
     * @param player       owner of the display
     * @param location     where it starts
     * @param textTemplate template to render
     * @param context      placeholder context of the owner
     * @return the new display
     */
    private TextDisplay spawn(Player player, Location location, String textTemplate, PlaceholderContext context) {
        Component component = pipeline.render(textTemplate, context);
        Color colour = colour(background);
        return player.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setBillboard(billboard);
            entity.setTeleportDuration(3);
            entity.setViewRange(viewRange);
            entity.setShadowed(shadowed);
            entity.setSeeThrough(seeThrough);
            entity.setLineWidth(lineWidth);
            if (scale != 1.0F) {
                entity.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                        new Quaternionf()));
            }
            if (brightness >= 0) {
                entity.setBrightness(new Display.Brightness(brightness, brightness));
            }
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            if (colour == null) {
                entity.setDefaultBackground(false);
            } else {
                entity.setDefaultBackground(true);
                entity.setBackgroundColor(colour);
            }
            entity.text(component);
        });
    }

    /**
     * @param player  player being drawn above
     * @param context placeholder context
     * @return the highest priority profile that matches, or {@code null} for the default
     */
    private Profile select(Player player, PlaceholderContext context) {
        for (Profile profile : profiles) {
            if (profile.matches(player, context, pipeline)) {
                return profile;
            }
        }
        return null;
    }

    private void remove(UUID id) {
        renderedText.remove(id);
        TextDisplay display = displays.remove(id);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void removeAll() {
        for (TextDisplay display : displays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        renderedText.clear();
    }

    private static double clamp(double value) {
        return Math.max(-2.0D, Math.min(6.0D, value));
    }

    private static List<String> lower(List<String> input) {
        List<String> output = new ArrayList<>(input.size());
        for (String value : input) {
            output.add(value.toLowerCase(Locale.ROOT));
        }
        return output;
    }

    /**
     * Reads a background colour, {@code transparent} meaning none.
     *
     * @param value configured value
     * @return the colour, or {@code null} for a transparent background
     */
    private static Color colour(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("transparent")
                || value.equalsIgnoreCase("none")) {
            return null;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            long parsed = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                return Color.fromRGB((int) parsed);
            }
            if (hex.length() == 8) {
                int argb = (int) parsed;
                return Color.fromARGB(argb);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    /** One optional layout with its own offset, template and conditions. */
    private record Profile(String id, int priority, Double offset, String template, List<String> worlds,
                           List<String> permissions, List<String> placeholders,
                           ConfigurationSection conditions) {

        boolean matches(Player player, PlaceholderContext context, RenderPipeline pipeline) {
            List<String> worldPatterns = worlds;
            List<String> permissionChecks = permissions;
            List<String> placeholderChecks = placeholders;
            if (conditions != null) {
                List<String> extraWorlds = conditions.getStringList("worlds");
                if (!extraWorlds.isEmpty()) {
                    worldPatterns = lower(extraWorlds);
                }
                List<String> extraPermissions = conditions.getStringList("permissions");
                if (!extraPermissions.isEmpty()) {
                    permissionChecks = extraPermissions;
                }
                List<String> extraPlaceholders = conditions.getStringList("placeholders");
                if (!extraPlaceholders.isEmpty()) {
                    placeholderChecks = extraPlaceholders;
                }
            }
            if (!worldMatched(worldPatterns, player.getWorld().getName())) {
                return false;
            }
            for (String permission : permissionChecks) {
                if (permission.startsWith("!")) {
                    if (player.hasPermission(permission.substring(1))) {
                        return false;
                    }
                } else if (!player.hasPermission(permission)) {
                    return false;
                }
            }
            for (String entry : placeholderChecks) {
                if (!compare(entry, context, pipeline)) {
                    return false;
                }
            }
            return true;
        }

        private boolean compare(String entry, PlaceholderContext context, RenderPipeline pipeline) {
            String expanded = pipeline.placeholders().expand(entry, context);
            for (String operator : OPERATORS) {
                int index = expanded.indexOf(operator);
                if (index < 0) {
                    continue;
                }
                String left = expanded.substring(0, index).trim();
                String right = expanded.substring(index + operator.length()).trim();
                switch (operator) {
                    case ">=", "<=", ">", "<" -> {
                        Double a = number(left);
                        Double b = number(right);
                        if (a == null || b == null) {
                            return false;
                        }
                        return switch (operator) {
                            case ">=" -> a >= b;
                            case "<=" -> a <= b;
                            case ">" -> a > b;
                            default -> a < b;
                        };
                    }
                    case "!=" -> {
                        return !left.equalsIgnoreCase(right);
                    }
                    case "contains" -> {
                        return left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
                    }
                    default -> {
                        return left.equalsIgnoreCase(right);
                    }
                }
            }
            return false;
        }

        private static Double number(String text) {
            try {
                return Double.valueOf(text.replace(",", "").replace("%", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static boolean worldMatched(List<String> patterns, String world) {
            if (patterns.isEmpty()) {
                return true;
            }
            boolean hasPositive = false;
            boolean positiveHit = false;
            String name = world.toLowerCase(Locale.ROOT);
            for (String raw : patterns) {
                boolean negated = raw.startsWith("!");
                String pattern = negated ? raw.substring(1) : raw;
                if (!negated) {
                    hasPositive = true;
                }
                if (glob(pattern, name)) {
                    if (negated) {
                        return false;
                    }
                    positiveHit = true;
                }
            }
            return !hasPositive || positiveHit;
        }

        private static boolean glob(String pattern, String value) {
            if (!pattern.contains("*")) {
                return pattern.equals(value);
            }
            StringBuilder regex = new StringBuilder();
            for (char c : pattern.toCharArray()) {
                if (c == '*') {
                    regex.append(".*");
                } else {
                    regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
                }
            }
            return value.matches(regex.toString());
        }
    }
}
