package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RconSettingsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void missingConfigurationDisablesRconWithoutFailingJudging() throws Exception {
        assertTrue(RconSettings.load(temporaryDirectory.resolve("judge.yml"), Map.of()).isEmpty());
    }

    @Test
    void environmentCanConfigureRconAndOverridesJudgeYaml() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(
                config,
                """
                rcon:
                  host: yaml-host
                  port: 25575
                  password: example-value
                  connect_timeout_seconds: 4
                  read_timeout_seconds: 6
                """);

        RconSettings settings =
                RconSettings.load(
                                config,
                                Map.of(
                                        "SCENARIOCRAFT_RCON_HOST", "127.0.0.1",
                                        "SCENARIOCRAFT_RCON_PORT", "25580",
                                        "SCENARIOCRAFT_RCON_PASSWORD", "environment-example"))
                        .orElseThrow();

        assertEquals("127.0.0.1", settings.host());
        assertEquals(25_580, settings.port());
        assertEquals(Duration.ofSeconds(4), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(6), settings.readTimeout());
    }

    @Test
    void partialConfigurationWithoutPasswordFailsEarly() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RconSettings.load(
                                temporaryDirectory.resolve("judge.yml"),
                                Map.of("SCENARIOCRAFT_RCON_HOST", "127.0.0.1")));
    }
}
