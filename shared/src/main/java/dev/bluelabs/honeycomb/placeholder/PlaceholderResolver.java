package dev.bluelabs.honeycomb.placeholder;

/**
 * Resolves one placeholder id such as {@code player_ping}.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * @param context render context
     * @return the replacement value, or {@code null} when the value cannot be
     * produced right now (the placeholder then stays untouched)
     */
    String resolve(PlaceholderContext context);
}
