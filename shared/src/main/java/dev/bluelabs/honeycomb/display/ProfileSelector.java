package dev.bluelabs.honeycomb.display;

import java.util.Collection;

/**
 * Picks the profile a player should see.
 */
public final class ProfileSelector {

    private ProfileSelector() {
    }

    /**
     * Selects the highest priority profile that applies to the context. When
     * several profiles share the highest priority the first one declared wins,
     * which keeps configuration order meaningful.
     *
     * @param profiles candidate profiles
     * @param context  match context
     * @param <T>      profile type
     * @return the selected profile, or {@code null} when nothing matches
     */
    public static <T extends DisplayProfile> T select(Collection<T> profiles, MatchContext context) {
        T best = null;
        for (T profile : profiles) {
            if (!profile.appliesTo(context)) {
                continue;
            }
            if (best == null || profile.priority() > best.priority()) {
                best = profile;
            }
        }
        return best;
    }
}
