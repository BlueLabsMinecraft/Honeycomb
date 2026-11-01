package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValuesTest {

    private YamlConfiguration yaml(String content) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(content);
        return configuration;
    }

    @Test
    void readsLists() throws InvalidConfigurationException {
        YamlConfiguration configuration = yaml("""
                header:
                  - one
                  - two
                """);
        assertEquals(List.of("one", "two"), ConfigValues.lines(configuration, "header"));
    }

    @Test
    void acceptsASingleScalarAsOneLine() throws InvalidConfigurationException {
        YamlConfiguration configuration = yaml("header: just one line\n");
        assertEquals(List.of("just one line"), ConfigValues.lines(configuration, "header"));
    }

    @Test
    void missingKeysProduceEmptyLists() throws InvalidConfigurationException {
        assertTrue(ConfigValues.lines(yaml("{}"), "nope").isEmpty());
    }

    @Test
    void invalidIntervalsFallBackAndReport() throws InvalidConfigurationException {
        ValidationReport report = new ValidationReport();
        int interval = ConfigValues.interval(yaml("update-interval: 0\n"), "update-interval", 20, "test.yml", report);
        assertEquals(20, interval);
        assertTrue(report.hasWarnings());
    }

    @Test
    void blankTextUsesTheFallback() throws InvalidConfigurationException {
        assertEquals("fallback", ConfigValues.text(yaml("name: ''\n"), "name", "fallback"));
    }

    @Test
    void validationReportCountsSeverities() {
        ValidationReport report = new ValidationReport();
        report.info("a", "note");
        report.warning("b", "careful");
        report.error("c", "broken");
        assertTrue(report.hasErrors());
        assertEquals(1, report.issues(ValidationReport.Severity.WARNING).size());
        assertEquals("1 warning(s), 1 error(s)", report.summary());
    }
}
