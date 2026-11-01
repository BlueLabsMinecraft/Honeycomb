package dev.bluelabs.honeycomb.placeholder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The table of internal placeholders.
 *
 * <p>Ids are stored without percent signs and are case insensitive.</p>
 */
public final class PlaceholderRegistry {

    private final Map<String, PlaceholderResolver> resolvers = new LinkedHashMap<>();

    /**
     * Registers or replaces a placeholder.
     *
     * @param id       placeholder id without percent signs
     * @param resolver resolver for the id
     */
    public void register(String id, PlaceholderResolver resolver) {
        if (id == null || id.isBlank() || resolver == null) {
            return;
        }
        resolvers.put(normalise(id), resolver);
    }

    /**
     * Removes a placeholder.
     *
     * @param id placeholder id
     * @return true when a resolver was removed
     */
    public boolean unregister(String id) {
        return resolvers.remove(normalise(id)) != null;
    }

    /**
     * @param id placeholder id without percent signs
     * @return true when this registry can resolve the id
     */
    public boolean contains(String id) {
        return resolvers.containsKey(normalise(id));
    }

    /**
     * @param id      placeholder id
     * @param context render context
     * @return the resolved value, or {@code null} when unknown
     */
    public String resolve(String id, PlaceholderContext context) {
        PlaceholderResolver resolver = resolvers.get(normalise(id));
        return resolver == null ? null : resolver.resolve(context);
    }

    /**
     * @return every registered id, sorted by registration order
     */
    public Set<String> ids() {
        return Collections.unmodifiableSet(resolvers.keySet());
    }

    /**
     * @return number of registered placeholders
     */
    public int size() {
        return resolvers.size();
    }

    private static String normalise(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
