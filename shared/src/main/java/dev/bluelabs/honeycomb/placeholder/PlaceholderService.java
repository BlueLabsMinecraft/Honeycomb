package dev.bluelabs.honeycomb.placeholder;

import dev.bluelabs.honeycomb.scheduler.TickClock;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Expands {@code %placeholder%} text.
 *
 * <p>Internal placeholders resolve first, then an optional external provider
 * (PlaceholderAPI) sees whatever is left. Unknown placeholders are left untouched
 * so an owner can see exactly which one is wrong.</p>
 */
public final class PlaceholderService {

    /** Prefixes owned by Honeycomb, used to report genuinely unknown ids. */
    private static final String[] INTERNAL_PREFIXES = {
            "player_", "server_", "world", "date", "time", "econ_", "network_", "honeycomb_"
    };

    private final PlaceholderRegistry registry;
    private final TickClock clock;
    private final Map<String, CachedValue> cache = new HashMap<>();
    private final Set<String> unknownIds = new LinkedHashSet<>();

    private ExternalPlaceholderResolver externalResolver;
    private int cacheTtlTicks = 10;
    private boolean warnUnknown = true;

    /**
     * @param registry placeholder registry
     * @param clock    shared tick clock used for cache expiry
     */
    public PlaceholderService(PlaceholderRegistry registry, TickClock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Applies runtime configuration and clears the cache.
     *
     * @param cacheTtlTicks how long resolved values may be reused, in ticks
     * @param warnUnknown   whether unknown internal placeholders are recorded
     */
    public void configure(int cacheTtlTicks, boolean warnUnknown) {
        this.cacheTtlTicks = Math.max(0, cacheTtlTicks);
        this.warnUnknown = warnUnknown;
        this.cache.clear();
    }

    /**
     * @param resolver external provider, or {@code null} to disable
     */
    public void setExternalResolver(ExternalPlaceholderResolver resolver) {
        this.externalResolver = resolver;
        this.cache.clear();
    }

    /**
     * @return the internal registry, so the API can add placeholders
     */
    public PlaceholderRegistry registry() {
        return registry;
    }

    /**
     * Expands every placeholder in the input, without caching.
     *
     * @param input   source text
     * @param context render context
     * @return the expanded text
     */
    public String expand(String input, PlaceholderContext context) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) {
            return input == null ? "" : input;
        }
        String internal = expandInternal(input, context);
        if (externalResolver != null && internal.indexOf('%') >= 0) {
            String external = externalResolver.resolve(context.player(), internal);
            return external == null ? internal : external;
        }
        return internal;
    }

    private String expandInternal(String input, PlaceholderContext context) {
        StringBuilder output = new StringBuilder(input.length() + 16);
        int index = 0;
        while (index < input.length()) {
            int open = input.indexOf('%', index);
            if (open < 0) {
                output.append(input, index, input.length());
                break;
            }
            int close = input.indexOf('%', open + 1);
            if (close < 0) {
                // Unpaired percent sign: copy the rest verbatim.
                output.append(input, index, input.length());
                break;
            }
            output.append(input, index, open);
            String id = input.substring(open + 1, close);
            String value = id.isEmpty() ? null : registry.resolve(id, context);
            if (value != null) {
                output.append(value);
            } else {
                // Keep the literal text so authors can see what did not resolve.
                output.append('%').append(id).append('%');
                if (!id.isEmpty() && !registry.contains(id)) {
                    recordUnknown(id);
                }
            }
            index = close + 1;
        }
        return output.toString();
    }

    private void recordUnknown(String id) {
        if (!warnUnknown || id.isEmpty() || id.indexOf('%') >= 0) {
            return;
        }
        for (String prefix : INTERNAL_PREFIXES) {
            if (id.startsWith(prefix)) {
                unknownIds.add(id);
                return;
            }
        }
    }

    /**
     * Expands placeholders with a short lived cache, for display updates that run
     * several times per second.
     *
     * @param input   source text
     * @param context render context
     * @return the expanded text
     */
    public String expandCached(String input, PlaceholderContext context) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0 || cacheTtlTicks <= 0) {
            return expand(input, context);
        }
        String key = context.cacheKey() + '\u0000' + input;
        long now = clock.tick();
        CachedValue cached = cache.get(key);
        if (cached != null && cached.expiresAtTick > now) {
            return cached.value;
        }
        String resolved = expand(input, context);
        cache.put(key, new CachedValue(resolved, now + cacheTtlTicks));
        return resolved;
    }

    /**
     * Drops expired cache entries so the cache cannot grow without bound.
     */
    public void sweepCache() {
        long now = clock.tick();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtTick <= now);
    }

    /**
     * @return placeholder ids that belong to Honeycomb but could not be resolved
     */
    public Set<String> unknownPlaceholders() {
        return Collections.unmodifiableSet(unknownIds);
    }

    /**
     * Clears the recorded unknown placeholder list.
     */
    public void clearUnknown() {
        unknownIds.clear();
    }

    /**
     * @return number of cached values, for diagnostics
     */
    public int cacheSize() {
        return cache.size();
    }

    private record CachedValue(String value, long expiresAtTick) {
    }
}
