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

class RconConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void noEnvironmentOrJudgeYamlLeavesRconOptional() throws Exception {
        assertTrue(RconConfig.load(temporaryDirectory.resolve("judge.yml"), Map.of()).isEmpty());
    }

    @Test
    void environmentOverridesJudgeYamlWithoutExposingThePassword() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, """
                rcon:
                  host: file-host
                  port: 25575
                  password: file-secret
                  timeout-seconds: 9
                """);

        RconConfig loaded = RconConfig.load(
                        config,
                        Map.of(
                                "SCENARIOCRAFT_RCON_HOST", "127.0.0.1",
                                "SCENARIOCRAFT_RCON_PORT", "25585",
                                "SCENARIOCRAFT_RCON_PASSWORD", "env-secret",
                                "SCENARIOCRAFT_RCON_TIMEOUT_SECONDS", "3"))
                .orElseThrow();

        assertEquals("127.0.0.1", loaded.host());
        assertEquals(25585, loaded.port());
        assertEquals("env-secret", loaded.password());
        assertEquals(Duration.ofSeconds(3), loaded.timeout());
    }

    @Test
    void partialConfigurationFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RconConfig.load(
                        temporaryDirectory.resolve("judge.yml"),
                        Map.of("SCENARIOCRAFT_RCON_PASSWORD", "secret")));
    }

    @Test
    void nullYamlTimeoutIsReportedAsInvalidConfiguration() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, """
                rcon:
                  host: localhost
                  port: 25575
                  password: secret
                  timeout-seconds:
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> RconConfig.load(config, Map.of()));
    }

    @Test
    void malformedYamlIsReportedAsInvalidConfiguration() throws Exception {
        Path config = temporaryDirectory.resolve("judge.yml");
        Files.writeString(config, "rcon: [unterminated");

        assertThrows(
                IllegalArgumentException.class,
                () -> RconConfig.load(config, Map.of()));
    }
}
