package dev.bluelabs.honeycomb.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Central access point for all text parsing.
 *
 * <p>Every user facing string in Honeycomb is MiniMessage. Config authors
 * occasionally paste legacy {@code &}-colour codes, so a parse failure falls back
 * to the legacy serializer instead of throwing and breaking a display update.</p>
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /**
     * Parses MiniMessage input into a component.
     *
     * @param input raw text, may be {@code null}
     * @return the parsed component, never {@code null}
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(input);
        } catch (RuntimeException exception) {
            // Malformed MiniMessage: fall back to legacy colour codes, then plain text.
            try {
                return LEGACY_AMPERSAND.deserialize(input);
            } catch (RuntimeException ignored) {
                return Component.text(input);
            }
        }
    }

    /**
     * Serialises a component with section sign colour codes. Only used where an
     * API still expects a {@link String} instead of a component.
     *
     * @param component the component to serialise
     * @return the legacy string representation
     */
    public static String toLegacy(Component component) {
        return LEGACY_SECTION.serialize(component == null ? Component.empty() : component);
    }

    /**
     * Removes MiniMessage tags, leaving the plain characters.
     *
     * @param input raw text
     * @return the text without tags
     */
    public static String stripTags(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return MINI_MESSAGE.stripTags(input);
    }

    /**
     * Escapes MiniMessage tags so user provided text is shown literally.
     *
     * @param input raw text
     * @return the escaped text
     */
    public static String escape(String input) {
        return input == null ? "" : MINI_MESSAGE.escapeTags(input);
    }
}
