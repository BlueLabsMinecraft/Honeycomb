package dev.bluelabs.honeycomb.display;

/**
 * Common part of every display profile: a name, a priority and the conditions
 * that decide which players it applies to.
 */
public abstract class DisplayProfile {

    private final String name;
    private final int priority;
    private final boolean enabled;
    private final ProfileConditions conditions;

    /**
     * @param name       profile name as written in configuration
     * @param priority   higher priority wins when several profiles match
     * @param enabled    whether the profile may be used at all
     * @param conditions conditions required for the profile to apply
     */
    protected DisplayProfile(String name, int priority, boolean enabled, ProfileConditions conditions) {
        this.name = name;
        this.priority = priority;
        this.enabled = enabled;
        this.conditions = conditions == null ? ProfileConditions.none() : conditions;
    }

    /**
     * @return the profile name
     */
    public String name() {
        return name;
    }

    /**
     * @return the selection priority
     */
    public int priority() {
        return priority;
    }

    /**
     * @return true when this profile may be used
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return the conditions for this profile
     */
    public ProfileConditions conditions() {
        return conditions;
    }

    /**
     * @param context match context
     * @return true when this profile applies to that context
     */
    public boolean appliesTo(MatchContext context) {
        return enabled && conditions.matches(context);
    }
}
