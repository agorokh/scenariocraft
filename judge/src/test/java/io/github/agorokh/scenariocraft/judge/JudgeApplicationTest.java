package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JudgeApplicationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dryRunJudgesCommittedFixtureEndToEndWithoutNetwork() throws Exception {
        Path runtime = copyFixtureRuntime();
        Path round = runtime.resolve("rounds/round-20260721-193000");
        StringWriter output = new StringWriter();

        int status = new JudgeApplication().run(
                round,
                runtime.resolve("judge/personas.yml"),
                runtime.resolve("judge/rubric.md"),
                new StubPersonaJudge(),
                new PrintWriter(output),
                new PrintWriter(new StringWriter()));

        assertEquals(0, status);
        JsonObject results = JsonParser.parseString(Files.readString(round.resolve("results.json")))
                .getAsJsonObject();
        assertEquals("Alex", results.getAsJsonObject("winner").get("player").getAsString());
        assertEquals(2, results.getAsJsonArray("contestants").get(0).getAsJsonObject()
                .getAsJsonArray("verdicts").size());
        String losingComment = results.getAsJsonArray("contestants").get(1).getAsJsonObject()
                .getAsJsonArray("verdicts").get(0).getAsJsonObject()
                .get("comment").getAsString();
        assertTrue(losingComment.contains("genuine detail worth celebrating"));
        assertFalse(losingComment.toLowerCase().matches(".*(awful|lazy|stupid|terrible).*"));
        assertTrue(output.toString().contains("Winner: Alex"));
    }

    @Test
    void twoPersonaFailuresWriteNoWinnerAndReturnNonZero() throws Exception {
        Path runtime = copyFixtureRuntime();
        Path round = runtime.resolve("rounds/round-20260721-193000");
        StringWriter output = new StringWriter();
        StringWriter diagnostics = new StringWriter();
        PersonaJudge failing = (persona, task, rubric, plotId, images) -> {
            throw new JudgeException("simulated timeout");
        };

        int status = new JudgeApplication().run(
                round,
                runtime.resolve("judge/personas.yml"),
                runtime.resolve("judge/rubric.md"),
                failing,
                new PrintWriter(output),
                new PrintWriter(diagnostics));

        assertEquals(1, status);
        JsonObject results = JsonParser.parseString(Files.readString(round.resolve("results.json")))
                .getAsJsonObject();
        assertTrue(results.get("no_winner").getAsBoolean());
        assertFalse(results.has("winner"));
        assertTrue(results.get("reason").getAsString().contains("at least 2 are required"));
        assertTrue(output.toString().contains("No winner:"));
        assertTrue(diagnostics.toString().contains("attempt 2 failed"));
    }

    @Test
    void runtimeDoesNotContainBukkit() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.Bukkit"));
    }

    private Path copyFixtureRuntime() throws Exception {
        Path source = Path.of(System.getProperty("scenariocraft.repoRoot"))
                .resolve("judge/src/test/resources/fixtures/runtime");
        Path destination = temporaryDirectory.resolve("runtime");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return destination;
    }
}
