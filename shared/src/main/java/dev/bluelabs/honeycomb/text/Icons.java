package dev.bluelabs.honeycomb.text;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Item and block sprites for MiniMessage text.
 *
 * <p>Minecraft 1.21.9 understands a {@code sprite} component, and MiniMessage exposes
 * it as {@code <sprite:atlas:path>}. The item atlas is {@code minecraft:items}, so
 * every item in the game can be dropped into a tablist line, a sidebar row, a
 * bossbar title or a dialog by name.</p>
 */
public final class Icons {

    /** Atlas holding item and block textures. */
    public static final String ATLAS = "minecraft:items";

    /** The icons the picker offers before anyone types a name. */
    public static final List<String> COMMON = List.of(
            "diamond", "emerald", "gold_ingot", "iron_ingot", "netherite_ingot",
            "coal", "redstone", "lapis_lazuli", "amethyst_shard", "quartz",
            "apple", "golden_apple", "bread", "cooked_beef", "sweet_berries",
            "ender_pearl", "blaze_powder", "arrow", "totem_of_undying", "nether_star",
            "experience_bottle", "enchanted_book", "chest", "hopper", "beacon",
            "player_head", "heart_of_the_sea", "elytra", "shulker_shell", "trial_key");

    private Icons() {
    }

    /**
     * Builds the sprite tag for an item.
     *
     * @param item item name such as {@code diamond}, or a full path
     * @return the MiniMessage tag
     */
    public static String sprite(String item) {
        String key = clean(item);
        String path = key.contains(":") || key.contains("/") ? key : "minecraft:item/" + key;
        return "<sprite:\"" + ATLAS + "\":\"" + path + "\">";
    }

    /**
     * Builds the sprite tag for a Bukkit material.
     *
     * @param material material
     * @return the MiniMessage tag
     */
    public static String sprite(Material material) {
        return sprite(material.getKey().getKey());
    }

    /**
     * @param item raw input
     * @return a lower case name with spaces turned into underscores
     */
    public static String clean(String item) {
        String key = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
        return key.replace(' ', '_');
    }

    /**
     * @param item raw input
     * @return true when the name looks like something the game could know
     */
    public static boolean plausible(String item) {
        String key = clean(item);
        return key.matches("[a-z0-9_./:-]{2,64}");
    }
}
