package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BattleResultsReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingRoundsDirectoryIsTheFriendlyNoResultsPath() throws Exception {
        BattleResultsReader reader =
                new BattleResultsReader(temporaryDirectory.resolve("rounds"));

        assertTrue(reader.latest().isEmpty());
    }

    @Test
    void findsNewestDirectoryThatActuallyHasResults() throws Exception {
        Path rounds = temporaryDirectory.resolve("rounds");
        Path judged = rounds.resolve("round-20260721-193000");
        Files.createDirectories(judged);
        Files.writeString(judged.resolve("results.txt"), winningResults());
        Files.createDirectories(rounds.resolve("round-20260721-194500"));

        BattleResultsReader.LatestResult latest =
                new BattleResultsReader(rounds).latest().orElseThrow();

        assertEquals("round-20260721-193000", latest.summary().roundId());
        assertEquals("Alex", latest.summary().winner().player());
        assertEquals("p1", latest.summary().winner().plotId());
        assertEquals(2, latest.summary().contestants().size());
        assertTrue(new BattleResultsReader(rounds).latestRound().isEmpty());
    }

    @Test
    void rejectsJsonOrTrailingTextInsteadOfLeakingItToPlayers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BattleResultsReader.parse("""
                        {"winner":"Alex"}
                        """));

        assertThrows(
                IllegalArgumentException.class,
                () -> BattleResultsReader.parse(winningResults() + "raw trailing text\n"));
    }

    @Test
    void parsesAQuorumFailureWithoutInventingAWinner() {
        BattleResultSummary summary = BattleResultsReader.parse("""
                Round: round-20260721-193000
                Task: A dragon treehouse

                Alex (p1)
                  Failed: Builder Bob attempt 2 failed

                No winner: only 1 valid judge; at least 2 are required.
                """);

        assertFalse(summary.hasWinner());
        assertTrue(summary.noWinnerReason().startsWith("No winner:"));
    }

    static String winningResults() {
        return """
                Round: round-20260721-193000
                Task: A dragon treehouse

                Alex (p1)
                  Builder Bob: 8.75 — The leafy wings are a genuine detail worth celebrating.
                  Redstone Rina: 8.75 — The {bright} windows make this build feel welcoming.
                  Mean: 8.75

                Sam (p2)
                  Builder Bob: 6.75 — The tall doorway gives the castle a strong shape.
                  Redstone Rina: 6.75 — The roof has a playful silhouette.
                  Mean: 6.75

                Winner: Alex with 8.75
                """;
    }
}
