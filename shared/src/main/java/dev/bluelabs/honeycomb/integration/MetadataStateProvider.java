package dev.bluelabs.honeycomb.integration;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AFK, nickname and vanish detection through plugin metadata.
 *
 * <p>EssentialsX, CMI and the popular vanish plugins all publish state as player
 * metadata. The keys are configurable because each plugin picks its own; Honeycomb
 * never guesses a state it cannot read.</p>
 */
public final class MetadataStateProvider implements PlayerStateProvider {

    private final Set<String> afkKeys;
    private final Set<String> vanishKeys;
    private final boolean useNicknames;
    private final String source;

    /**
     * @param afkKeys      metadata keys that mark a player as AFK
     * @param vanishKeys   metadata keys that mark a player as vanished
     * @param useNicknames whether display name changes are treated as nicknames
     * @param source       description of what answered, for diagnostics
     */
    public MetadataStateProvider(Set<String> afkKeys, Set<String> vanishKeys, boolean useNicknames, String source) {
        this.afkKeys = afkKeys;
        this.vanishKeys = vanishKeys;
        this.useNicknames = useNicknames;
        this.source = source;
    }

    @Override
    public boolean afk(Player player) {
        for (String key : afkKeys) {
            if (isTrue(player, key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String nickname(Player player) {
        if (!useNicknames) {
            return null;
        }
        // Adventure display names cover both modern components and legacy colour codes.
        String plain = PlainTextComponentSerializer.plainText().serialize(player.displayName());
        return plain.isBlank() || plain.equals(player.getName()) ? null : plain;
    }

    @Override
    public boolean vanished(Player player) {
        for (String key : vanishKeys) {
            if (isTrue(player, key)) {
                return true;
            }
        }
        return false;
    }

    // Bukkit's metadata API is marked deprecated in newer releases, but it is still
    // the only interface EssentialsX, CMI and the vanish plugins publish state through.
    @SuppressWarnings("deprecation")
    private boolean isTrue(Player player, String key) {
        if (key == null || key.isBlank() || !player.hasMetadata(key)) {
            return false;
        }
        List<MetadataValue> values = player.getMetadata(key);
        for (MetadataValue value : values) {
            if (value == null) {
                continue;
            }
            if ("true".equals(value.asString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String sourceName() {
        return source;
    }
}
