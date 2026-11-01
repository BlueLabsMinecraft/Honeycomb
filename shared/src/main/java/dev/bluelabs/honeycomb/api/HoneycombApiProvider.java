package dev.bluelabs.honeycomb.api;

import java.util.Objects;

/**
 * Holds the active API implementation.
 *
 * <p>Other plugins should check {@link #isAvailable()} before use, for example in
 * their {@code onEnable}, and listen for Honeycomb's reload event if they cache
 * rendered text.</p>
 */
public final class HoneycombApiProvider {

    private static HoneycombApi api;

    private HoneycombApiProvider() {
    }

    /**
     * @return the API
     * @throws IllegalStateException when Honeycomb is not enabled
     */
    public static HoneycombApi get() {
        HoneycombApi current = api;
        if (current == null) {
            throw new IllegalStateException("Honeycomb is not enabled");
        }
        return current;
    }

    /**
     * @return true when Honeycomb is enabled and the API can be used
     */
    public static boolean isAvailable() {
        return api != null;
    }

    /**
     * Registers the implementation. Called by Honeycomb itself.
     *
     * @param implementation the API implementation
     */
    public static void register(HoneycombApi implementation) {
        api = Objects.requireNonNull(implementation, "implementation");
    }

    /**
     * Clears the implementation. Called by Honeycomb on disable.
     */
    public static void unregister() {
        api = null;
    }
}
