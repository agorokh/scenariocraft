package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage", "You are a clown"));
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

    @Test
    void rejectsManifestFilesAboveTheHeapBound() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        try (FileChannel channel = FileChannel.open(
                manifest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(JudgeRound.MAX_MANIFEST_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }

        assertThrows(java.io.IOException.class, () -> JudgeRound.read(manifest));
    }

    @Test
    void rejectsExcessivePlotCountsAndMetadataLengths() {
        List<JudgeRound.Plot> plots = new ArrayList<>();
        for (int index = 1; index <= JudgeRound.MAX_PLOTS + 1; index++) {
            plots.add(new JudgeRound.Plot(
                    "p" + index, "Player" + index,
                    List.of(0, 64, 0), List.of(33, 40, 33)));
        }

        assertThrows(IllegalArgumentException.class, () -> new JudgeRound(
                1, "round-20260721-193000", "Build a cottage", "battle_world", plots));
        assertThrows(IllegalArgumentException.class, () -> round("x".repeat(513), "Alex"));
        assertThrows(IllegalArgumentException.class, () -> round(
                "Build a cottage", "x".repeat(65)));
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
