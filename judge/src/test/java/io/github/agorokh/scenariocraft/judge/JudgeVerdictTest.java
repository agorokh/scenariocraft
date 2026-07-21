package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JudgeVerdictTest {
    private static final Scores SCORES = new Scores(7, 7, 7, 7);

    @Test
    void acceptsDecimalsAndInitialismsWithoutFalseSentenceBreaks() {
        assertDoesNotThrow(() -> new JudgeVerdict(
                "Professor Fixture",
                "The proportions support the task.",
                SCORES,
                "Your U.S. flag has a bright color pattern. Add 2.5 blocks of trim next."));
    }

    @Test
    void rejectsCruelOrImprovementFirstModelComments() {
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof is ugly and boring. Add more blocks next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Try adding more windows. Keep working on the roof next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your clear walls make a recognizable outline. "
                        + "This is disgusting and nobody will like it."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your doorway creates a focal point despite having no talent. "
                        + "Try adding trim next."));
    }

    @Test
    void acceptsConcreteStrengthWithoutRequiringAKeywordList() {
        assertDoesNotThrow(() -> verdict(
                "The spiral roof draws the eye toward the doorway. "
                        + "Consider adding trim around the windows next."));
    }

    @Test
    void acceptsOneBlockAsTheConcreteStrengthInAnEdgeCase() {
        assertDoesNotThrow(() -> verdict(
                "Your stone block creates a clear starting point. "
                        + "Try building a small roof around it next."));
    }

    @Test
    void acceptsNeutralNegativeWordsInsideConcretePraise() {
        assertDoesNotThrow(() -> verdict(
                "The roof is strong without extra trim. Add a chimney next."));
    }

    @Test
    void rejectsObservationWithoutAConcreteStrengthAndOversizedText() {
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "I saw your build. Add a roof next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "The roof is strong " + "x".repeat(JudgeVerdict.MAX_COMMENT_LENGTH)
                        + ". Add a chimney next."));
        assertThrows(IllegalArgumentException.class, () -> new JudgeVerdict(
                "Professor Fixture",
                "x".repeat(JudgeVerdict.MAX_REASONING_LENGTH + 1),
                SCORES,
                "The roof is strong. Add a chimney next."));
    }

    @Test
    void rejectsUnsafeOrCruelPublishedReasoning() {
        assertThrows(IllegalArgumentException.class, () -> new JudgeVerdict(
                "Professor Fixture", "This build is disgusting.", SCORES,
                "The roof is strong. Add a chimney next."));
        assertThrows(IllegalArgumentException.class, () -> new JudgeVerdict(
                "Professor Fixture", "Safe words\u202ehidden", SCORES,
                "The roof is strong. Add a chimney next."));
    }

    @Test
    void rejectsMultilineComments() {
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof has a strong shape.\nAdd more blocks next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof has a strong shape.\u2028Add more blocks next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof has a strong shape. Add more \u202eblocks next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof has a strong shape. Add more "
                        + new String(Character.toChars(0xE0001)) + "blocks next."));
    }

    private JudgeVerdict verdict(String comment) {
        return new JudgeVerdict(
                "Professor Fixture", "The build was reviewed.", SCORES, comment);
    }
}
