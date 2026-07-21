package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
