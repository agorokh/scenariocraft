package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ArenaConfigLoaderTest {
    @Test
    void packagedConfigProvidesEveryRequestedDefault() {
        BattleSettings settings = ArenaConfigLoader.load(packagedConfig());

        assertEquals(new ArenaSettings(33, 30, 64, 8, 4_000), settings.arena());
        assertEquals(new PhaseTimings(30, 60, 1_200, 900), settings.timings());
        assertTrue(settings.tasks().size() >= 24);
        assertEquals(settings.tasks().size(), new HashSet<>(settings.tasks()).size());
        assertTrue(settings.exemptNames().isEmpty());
        assertTrue(settings.allowAnyStart());
    }

    @Test
    void invalidValuesFailBeforeArenaWorkStarts() {
        YamlConfiguration invalidBudget = packagedConfig();
        invalidBudget.set("blocks-per-tick", 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ArenaConfigLoader.load(invalidBudget));

        YamlConfiguration excessivePlots = packagedConfig();
        excessivePlots.set("max-plots", 9);
        assertThrows(
                IllegalArgumentException.class,
                () -> ArenaConfigLoader.load(excessivePlots));
    }

    @Test
    void restrictedStartAllowsOperatorsAndConfiguredNames() {
        YamlConfiguration config = packagedConfig();
        config.set("allow-any-start", false);
        config.set("exempt-names", java.util.List.of("BuilderKid"));
        BattleSettings settings = ArenaConfigLoader.load(config);

        assertTrue(settings.canStart("builderkid", false));
        assertTrue(settings.canStart("Console", true));
        assertFalse(settings.canStart("Visitor", false));
        assertTrue(settings.isExempt("BUILDERKID"));
        assertFalse(settings.isExempt("Visitor"));
    }

    private static YamlConfiguration packagedConfig() {
        InputStream input =
                ArenaConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml");
        if (input == null) {
            throw new AssertionError("config.yml must be packaged as a resource");
        }
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
