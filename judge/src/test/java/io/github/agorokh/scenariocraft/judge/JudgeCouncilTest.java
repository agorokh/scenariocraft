package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JudgeCouncilTest {
    private static final String RUBRIC = "theme_fit creativity effort detail";
    private static final List<Persona> PERSONAS = List.of(
            new Persona("One", "Voice one"),
            new Persona("Two", "Voice two"),
            new Persona("Three", "Voice three"));

    @Test
    void ranksContestantsByMeanAcrossPersonaCriterionMeans() throws Exception {
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            int score = "p1".equals(plotId) ? 8 : 6;
            return verdict(persona.name(), score);
        };

        RoundResults results = run(judge);

        assertTrue(results.hasWinner());
        assertEquals("p1", results.winner().plotId());
        assertEquals(8.25, results.winner().mean());
        assertEquals(8.25, results.contestants().get(0).mean());
        assertEquals(6.25, results.contestants().get(1).mean());
    }

    @Test
    void retriesOnceThenFailsClosedWhenTwoPersonasStillFail() throws Exception {
        Map<String, AtomicInteger> calls = new HashMap<>();
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            calls.computeIfAbsent(persona.name(), ignored -> new AtomicInteger())
                    .incrementAndGet();
            if (!"One".equals(persona.name())) {
                throw new JudgeException("simulated timeout");
            }
            return verdict(persona.name(), 7);
        };

        RoundResults results = run(judge);

        assertFalse(results.hasWinner());
        assertEquals(true, results.noWinner());
        assertTrue(results.reason().contains("received 1 successful verdict"));
        assertEquals(2, calls.get("One").get());
        assertEquals(8, calls.get("Two").get() + calls.get("Three").get());
        assertEquals(2, results.contestants().get(0).failures().size());
    }

    @Test
    void oneTransientFailureUsesSecondAttemptAndStillCountsVerdict() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            if ("One".equals(persona.name()) && "p1".equals(plotId)
                    && calls.incrementAndGet() == 1) {
                throw new JudgeException("temporary error");
            }
            return verdict(persona.name(), "p1".equals(plotId) ? 8 : 6);
        };

        RoundResults results = run(judge);

        assertTrue(results.hasWinner());
        assertEquals(2, calls.get());
        assertNull(results.noWinner());
    }

    @Test
    void nonRetryableFailureStopsTheCouncilImmediately() {
        AtomicInteger calls = new AtomicInteger();
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            calls.incrementAndGet();
            throw new JudgeException("cancelled", null, false);
        };

        JudgeException exception = assertThrows(JudgeException.class, () -> run(judge));

        assertEquals("cancelled", exception.getMessage());
        assertEquals(1, calls.get());
    }

    @Test
    void failsClosedWhenContestantsHaveUnequalCouncilSizes() throws Exception {
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            if ("p1".equals(plotId) && "Three".equals(persona.name())) {
                throw new JudgeException("persona unavailable");
            }
            return verdict(persona.name(), "p1".equals(plotId) ? 8 : 6);
        };

        RoundResults results = run(judge);

        assertFalse(results.hasWinner());
        assertEquals(true, results.noWinner());
        assertTrue(results.reason().contains("unequal successful persona panels"));
        assertEquals(2, results.contestants().get(0).verdicts().size());
        assertEquals(3, results.contestants().get(1).verdicts().size());
    }

    @Test
    void failsClosedWhenEqualSizedCouncilsContainDifferentPersonas() throws Exception {
        PersonaJudge judge = (persona, task, rubric, plotId, images) -> {
            if (("p1".equals(plotId) && "Three".equals(persona.name()))
                    || ("p2".equals(plotId) && "Two".equals(persona.name()))) {
                throw new JudgeException("persona unavailable");
            }
            return verdict(persona.name(), "p1".equals(plotId) ? 8 : 6);
        };

        RoundResults results = run(judge);

        assertFalse(results.hasWinner());
        assertTrue(results.reason().contains("unequal successful persona panels"));
        assertEquals(2, results.contestants().get(0).verdicts().size());
        assertEquals(2, results.contestants().get(1).verdicts().size());
    }

    private RoundResults run(PersonaJudge judge) throws JudgeException {
        JudgeRound round = new JudgeRound(
                1,
                "round-20260721-193000",
                "Build a cottage",
                "battle_world",
                List.of(new JudgeRound.Plot(
                                "p1", "Alex", List.of(0, 64, 0), List.of(33, 40, 33)),
                        new JudgeRound.Plot(
                                "p2", "Sam", List.of(48, 64, 0), List.of(33, 40, 33))));
        JudgeConfig config = new JudgeConfig(2, PERSONAS, RUBRIC);
        return JudgeCouncil.judge(
                round,
                config,
                Map.of("p1", List.<JudgeImage>of(), "p2", List.<JudgeImage>of()),
                judge,
                new PrintWriter(new StringWriter()));
    }

    private JudgeVerdict verdict(String persona, int score) {
        return new JudgeVerdict(
                persona,
                "The build has a clear task connection and several intentional choices.",
                new Scores(score, score, score, score + 1),
                "Your entrance gives the build a clear focal point. "
                        + "Try another layer of texture to make it even stronger.");
    }
}
