package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the panel writes has to reach the live configuration.
 *
 * <p>The panel never edits the shipped YAML, it writes panel.yml and the values are
 * merged on top when a file is loaded. If that merge misses, every saved setting
 * silently does nothing, so the round trip is checked here for the files the panel
 * touches.</p>
 */
class PanelOverrideRoundTripTest {

    @TempDir
    Path folder;

    private File panelFile() {
        return folder.resolve("panel.yml").toFile();
    }

    private YamlConfiguration shipped(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (Exception broken) {
            throw new IllegalStateException(broken);
        }
        return configuration;
    }

    private static void save(PanelOverrides overrides) {
        try {
            overrides.save();
        } catch (java.io.IOException broken) {
            throw new IllegalStateException(broken);
        }
    }

    @Test
    void maintenanceToggleReachesTheLoadedFile() {
        PanelOverrides overrides = new PanelOverrides(panelFile(), null);
        overrides.set(ConfigType.MAINTENANCE, "maintenance.enabled", true);
        overrides.set(ConfigType.MAINTENANCE, "motd.fake-software", "Blue Labs");
        overrides.set(ConfigType.MAINTENANCE, "motd.line-1", "<red>hi");

        save(overrides);
        PanelOverrides reloaded = new PanelOverrides(panelFile(), null);
        reloaded.load();

        YamlConfiguration live = shipped("""
                maintenance:
                  enabled: false
                  kick-message:
                    - one
                motd:
                  enabled: false
                  line-1: original
                  fake-software: ""
                """);
        reloaded.apply(ConfigType.MAINTENANCE, live);

        assertTrue(live.getBoolean("maintenance.enabled"), "maintenance toggle was lost");
        assertEquals("Blue Labs", live.getString("motd.fake-software"));
        assertEquals("<red>hi", live.getString("motd.line-1"));
        assertEquals(1, live.getStringList("maintenance.kick-message").size(), "untouched keys stay");
    }

    @Test
    void tablistAndActionbarChangesReachTheLoadedFile() {
        PanelOverrides overrides = new PanelOverrides(panelFile(), null);
        overrides.set(ConfigType.TABLIST, "header", "<gold>NEW HEADER");
        overrides.set(ConfigType.TABLIST, "profiles.pvp.header", "<red>PVP");
        overrides.set(ConfigType.CONFIG, "actionbar.enabled", true);
        overrides.set(ConfigType.CONFIG, "actionbar.template", "<gray>%player_name%");

        save(overrides);
        PanelOverrides reloaded = new PanelOverrides(panelFile(), null);
        reloaded.load();

        YamlConfiguration tablist = shipped("""
                tablist:
                  enabled: true
                  header: old
                  profiles:
                    pvp:
                      header: old pvp
                """);
        reloaded.apply(ConfigType.TABLIST, tablist);
        assertEquals("<gold>NEW HEADER", tablist.getString("tablist.header"));
        assertEquals("<red>PVP", tablist.getString("tablist.profiles.pvp.header"));

        YamlConfiguration config = shipped("""
                actionbar:
                  enabled: false
                  template: ""
                """);
        reloaded.apply(ConfigType.CONFIG, config);
        assertTrue(config.getBoolean("actionbar.enabled"), "actionbar toggle was lost");
        assertEquals("<gray>%player_name%", config.getString("actionbar.template"));
    }

    @Test
    void nametagGroupsReachTheLoadedFile() {
        PanelOverrides overrides = new PanelOverrides(panelFile(), null);
        overrides.set(ConfigType.NAMETAGS, "groups.owner.prefix", "<red>[OWNER] ");
        overrides.set(ConfigType.NAMETAGS, "enabled", true);

        save(overrides);
        PanelOverrides reloaded = new PanelOverrides(panelFile(), null);
        reloaded.load();

        YamlConfiguration live = shipped("""
                nametags:
                  enabled: false
                  groups:
                    owner:
                      prefix: ""
                """);
        reloaded.apply(ConfigType.NAMETAGS, live);
        assertEquals("<red>[OWNER] ", live.getString("nametags.groups.owner.prefix"));
        assertTrue(live.getBoolean("nametags.enabled"));
    }

    @Test
    void panelFileStaysReadableWithoutDoubledRoots() {
        PanelOverrides overrides = new PanelOverrides(panelFile(), null);
        overrides.set(ConfigType.MAINTENANCE, "maintenance.enabled", true);
        overrides.set(ConfigType.TABLIST, "profiles.nether.header", "<red>hot");
        overrides.set(ConfigType.SCOREBOARDS, "pvp.title", "PVP");
        save(overrides);

        String written;
        try {
            written = java.nio.file.Files.readString(panelFile().toPath());
        } catch (java.io.IOException broken) {
            throw new IllegalStateException(broken);
        }
        assertTrue(written.contains("server:"), "maintenance belongs under its own namespace: " + written);
        assertFalse(written.contains("maintenance:\n  maintenance:"), "root stored twice: " + written);
        assertFalse(written.contains("tablist:\n  tablist:"), "root stored twice: " + written);
        assertFalse(written.contains("scoreboards:\n  scoreboards:"), "root stored twice: " + written);
    }
}
