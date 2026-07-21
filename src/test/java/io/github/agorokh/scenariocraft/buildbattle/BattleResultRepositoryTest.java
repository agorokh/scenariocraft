package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BattleResultRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void discoversLatestCopiedResultWithoutManifestOrJson() throws Exception {
        Path rounds = temporaryDirectory.resolve("rounds");
        Files.createDirectories(rounds.resolve("round-20260721-190000"));
        Path latest = Files.createDirectories(rounds.resolve("round-20260721-193000"));
        Files.writeString(latest.resolve("results.txt"), validResult("round-20260721-193000"));

        BattleResultRepository repository = new BattleResultRepository(rounds);

        assertEquals("round-20260721-193000", repository.latest().orElseThrow().roundId());
        assertEquals("Alex", repository.round("round-20260721-193000").orElseThrow().winner().orElseThrow().player());
        assertTrue(repository.round("round-20260721-194000").isEmpty());
    }

    @Test
    void historicalRoundsBeyondTheCandidateLimitDoNotDisableLatestReplay() throws Exception {
        Path rounds = temporaryDirectory.resolve("many-rounds");
        for (int second = 0; second < 257; second++) {
            Files.createDirectories(rounds.resolve("round-20260721-" + String.format("%06d", second)));
        }
        Path latest = rounds.resolve("round-20260721-000256");
        Files.writeString(latest.resolve("results.txt"), validResult("round-20260721-000256"));

        assertEquals(
                "round-20260721-000256",
                new BattleResultRepository(rounds).latest().orElseThrow().roundId());
    }

    @Test
    void unjudgedRecentRoundsDoNotHideAnOlderCompletedResult() throws Exception {
        Path rounds = temporaryDirectory.resolve("delayed-results");
        Path completed = Files.createDirectories(rounds.resolve("round-20260720-235959"));
        Files.writeString(
                completed.resolve("results.txt"), validResult("round-20260720-235959"));
        for (int second = 0; second < 257; second++) {
            Files.createDirectories(rounds.resolve("round-20260721-" + String.format("%06d", second)));
        }

        assertEquals(
                "round-20260720-235959",
                new BattleResultRepository(rounds).latest().orElseThrow().roundId());
    }

    @Test
    void requestedRoundRejectsAMismatchedResultHeader() throws Exception {
        Path rounds = temporaryDirectory.resolve("mismatch");
        Path round = Files.createDirectories(rounds.resolve("round-20260721-193000"));
        Files.writeString(round.resolve("results.txt"), validResult("round-20260721-190000"));

        assertThrows(
                java.io.IOException.class,
                () -> new BattleResultRepository(rounds).round("round-20260721-193000"));
    }

    static String validResult(String roundId) {
        return """
                Round: %s
                Task: A rocket-powered castle

                Alex (p1)
                  Captain Comet: 9.25 — The bright roof makes this castle feel welcoming.
                  Mean: 9.25

                Winner: Alex with 9.25
                """.formatted(roundId);
    }
}
