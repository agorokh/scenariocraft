package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JudgeRoundTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsTerminalControlCharactersInHumanReadableManifestFields() {
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage\u001b[2J", "Alex"));
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage", "Alex\u202e"));
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage\nHide this", "Alex"));
    }

    @Test
    void rejectsIncompleteOrNonSchemaPlotEntries() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, """
                {"schema":1,"round_id":"round-20260721-193000",
                 "task":"Build a cottage","world":"battle_world",
                 "plots":[{"plot_id":"foo","player":"Alex","size":[33,40,33]}]}
                """);

        assertThrows(IllegalArgumentException.class, () -> JudgeRound.read(manifest));
    }

    private JudgeRound round(String task, String player) {
        return new JudgeRound(
                1,
                "round-20260721-193000",
                task,
                "battle_world",
                List.of(new JudgeRound.Plot(
                        "p1", player, List.of(0, 64, 0), List.of(33, 40, 33))));
    }
}
