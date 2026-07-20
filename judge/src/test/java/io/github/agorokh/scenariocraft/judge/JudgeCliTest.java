package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeCliTest {
    @Test
    void liveRunRequiresApiKeyFromEnvironment() {
        StringWriter diagnostics = new StringWriter();

        int status = JudgeCli.run(
                new String[] {"--round", "somewhere"},
                Map.of(),
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics));

        assertEquals(2, status);
        assertTrue(diagnostics.toString().contains("OPENAI_API_KEY is required"));
    }

    @Test
    void rejectsUnknownArgumentsWithUsage() {
        StringWriter diagnostics = new StringWriter();

        int status = JudgeCli.run(
                new String[] {"--dry-run"},
                Map.of(),
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics));

        assertEquals(2, status);
        assertTrue(diagnostics.toString().contains("Usage: judge"));
    }

    @Test
    void rejectsInvalidConfiguredTimeoutBeforeNetworkUse() {
        StringWriter diagnostics = new StringWriter();

        int status = JudgeCli.run(
                new String[] {"--round", "somewhere"},
                Map.of(
                        "OPENAI_API_KEY", "test-placeholder",
                        "SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS", "zero"),
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics));

        assertEquals(2, status);
        assertTrue(diagnostics.toString().contains("must be a positive integer"));
    }
}
