package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel must be able to change values without rewriting the commented default
 * files, so overrides are stored separately and merged on load.
 */
class PanelOverridesTest {

    @Test
    void overrideReplacesFileValueAndKeepsEverythingElse(@TempDir Path dir) throws Exception {
        PanelOverrides overrides = new PanelOverrides(dir.resolve("panel.yml").toFile(), null);
        overrides.set("tablist.header", List.of("<shadow:#000000:1>Panel header"));
        overrides.set("tablist.update-interval", 5);

        YamlConfiguration target = new YamlConfiguration();
        target.set("tablist.header", List.of("file header"));
        target.set("tablist.footer", List.of("file footer"));
        target.set("tablist.update-interval", 40);

        overrides.apply(ConfigType.TABLIST, target);

        assertEquals(List.of("<shadow:#000000:1>Panel header"), target.getStringList("tablist.header"));
        assertEquals(5, target.getInt("tablist.update-interval"));
        assertEquals(List.of("file footer"), target.getStringList("tablist.footer"),
                "untouched keys keep their file value");
    }

    @Test
    void overridesSurviveASaveAndReload(@TempDir Path dir) throws Exception {
        File file = dir.resolve("panel.yml").toFile();
        PanelOverrides written = new PanelOverrides(file, null);
        written.set("scoreboards.default.title", "<red>Panel title");
        written.save();
        assertTrue(file.exists());

        PanelOverrides reloaded = new PanelOverrides(file, null);
        reloaded.load();

        YamlConfiguration target = new YamlConfiguration();
        target.set("scoreboards.default.title", "file title");
        reloaded.apply(ConfigType.SCOREBOARDS, target);
        assertEquals("<red>Panel title", target.getString("scoreboards.default.title"));
    }

    @Test
    void removingAnOverrideFallsBackToTheFile(@TempDir Path dir) {
        PanelOverrides overrides = new PanelOverrides(dir.resolve("panel.yml").toFile(), null);
        overrides.set("nametags.groups.default.prefix", "<panel>");
        overrides.set("nametags.groups.default.prefix", null);

        YamlConfiguration target = new YamlConfiguration();
        target.set("nametags.groups.default.prefix", "<file>");
        overrides.apply(ConfigType.NAMETAGS, target);
        assertEquals("<file>", target.getString("nametags.groups.default.prefix"));
    }
}
