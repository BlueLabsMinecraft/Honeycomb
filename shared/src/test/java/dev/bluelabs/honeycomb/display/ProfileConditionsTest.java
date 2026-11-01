package dev.bluelabs.honeycomb.display;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileConditionsTest {

    private final dev.bluelabs.honeycomb.config.ValidationReport report =
            new dev.bluelabs.honeycomb.config.ValidationReport();

    private ProfileConditions parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return ProfileConditions.parse(configuration, "test.yml", report);
    }

    private MatchContext context(String world, String group, Set<String> permissions) {
        return MatchContext.of(world, group, permissions::contains);
    }

    @Test
    void emptyConditionsMatchEveryone() throws InvalidConfigurationException {
        assertTrue(parse("priority: 1").matches(context("world", "default", Set.of())));
    }

    @Test
    void worldListMustContainTheWorld() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  worlds:
                    - world
                """);
        assertTrue(conditions.matches(context("world", "default", Set.of())));
        assertFalse(conditions.matches(context("world_nether", "default", Set.of())));
    }

    @Test
    void negatedWorldExcludesIt() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  worlds:
                    - "!world_nether"
                """);
        assertTrue(conditions.matches(context("world", "default", Set.of())));
        assertFalse(conditions.matches(context("world_nether", "default", Set.of())));
    }

    @Test
    void worldGlobMatches() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  worlds:
                    - "world_*"
                """);
        assertTrue(conditions.matches(context("world_nether", "default", Set.of())));
        assertFalse(conditions.matches(context("skyblock", "default", Set.of())));
    }

    @Test
    void permissionsAreRequired() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  permissions:
                    - honeycomb.staff
                """);
        assertFalse(conditions.matches(context("world", "default", Set.of())));
        assertTrue(conditions.matches(context("world", "default", Set.of("honeycomb.staff"))));
    }

    @Test
    void negatedPermissionBlocksHolders() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  permissions:
                    - "!honeycomb.bypass"
                """);
        assertTrue(conditions.matches(context("world", "default", Set.of())));
        assertFalse(conditions.matches(context("world", "default", Set.of("honeycomb.bypass"))));
    }

    @Test
    void groupsAreMatchedCaseInsensitively() throws InvalidConfigurationException {
        ProfileConditions conditions = parse("""
                conditions:
                  groups:
                    - Admin
                """);
        assertTrue(conditions.matches(context("world", "ADMIN", Set.of())));
        assertFalse(conditions.matches(context("world", "default", Set.of())));
    }
}
