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
    void environmentTrioOverridesJudgeYamlAndUsesDefaultTimeouts() throws Exception {
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
        assertEquals(Duration.ofSeconds(5), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(5), settings.readTimeout());
    }

    @Test
    void completeEnvironmentConfigurationIgnoresMalformedJudgeYaml() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, "not: [valid");

        RconSettings settings =
                RconSettings.load(
                                config,
                                Map.of(
                                        "SCENARIOCRAFT_RCON_HOST", "127.0.0.1",
                                        "SCENARIOCRAFT_RCON_PORT", "25580",
                                        "SCENARIOCRAFT_RCON_PASSWORD", "environment-example",
                                        "SCENARIOCRAFT_RCON_CONNECT_TIMEOUT_SECONDS", "3",
                                        "SCENARIOCRAFT_RCON_READ_TIMEOUT_SECONDS", "7"))
                        .orElseThrow();

        assertEquals("127.0.0.1", settings.host());
        assertEquals(25_580, settings.port());
        assertEquals(Duration.ofSeconds(3), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(7), settings.readTimeout());
    }

    @Test
    void legacyEnvironmentTrioIgnoresMalformedJudgeYaml() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, "rcon: [not valid");

        RconSettings settings =
                RconSettings.load(
                                config,
                                Map.of(
                                        "SCENARIOCRAFT_RCON_HOST", "127.0.0.1",
                                        "SCENARIOCRAFT_RCON_PORT", "25580",
                                        "SCENARIOCRAFT_RCON_PASSWORD", "environment-example",
                                        "SCENARIOCRAFT_RCON_TIMEOUT_SECONDS", "3"))
                        .orElseThrow();

        assertEquals(Duration.ofSeconds(3), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(3), settings.readTimeout());
    }

    @Test
    void legacyYamlTimeoutIsAccepted() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(
                config,
                """
                rcon:
                  host: yaml-host
                  port: 25575
                  password: example-value
                  timeout-seconds: 9
                """);

        RconSettings settings = RconSettings.load(config, Map.of()).orElseThrow();

        assertEquals(Duration.ofSeconds(9), settings.connectTimeout());
        assertEquals(Duration.ofSeconds(9), settings.readTimeout());
    }

    @Test
    void malformedOptionalJudgeYamlIsReportedAsConfigurationError() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, "rcon: [not valid");

        assertThrows(IllegalArgumentException.class, () -> RconSettings.load(config, Map.of()));
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
