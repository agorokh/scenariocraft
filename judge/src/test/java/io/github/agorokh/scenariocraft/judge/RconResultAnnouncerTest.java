package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RconResultAnnouncerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configuredAnnouncementUsesOnlyThePluginOwnedCommand() throws Exception {
        AtomicReference<String> command = new AtomicReference<>();
        RconResultAnnouncer announcer = new RconResultAnnouncer(
                temporaryDirectory.resolve("judge.yml"),
                Map.of(
                        "SCENARIOCRAFT_RCON_HOST", "127.0.0.1",
                        "SCENARIOCRAFT_RCON_PORT", "25575",
                        "SCENARIOCRAFT_RCON_PASSWORD", "secret"),
                (ignored, value) -> command.set(value));

        announcer.announce(null, null);

        assertEquals("battle announce-results", command.get());
    }
}
