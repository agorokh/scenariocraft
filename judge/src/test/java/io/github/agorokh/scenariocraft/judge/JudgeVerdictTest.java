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
    void rejectsCruelOrStrengthFreeModelComments() {
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof is ugly and boring. Add more blocks next."));
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "The build has a roof. Add more blocks next."));
    }

    @Test
    void rejectsMultilineComments() {
        assertThrows(IllegalArgumentException.class, () -> verdict(
                "Your roof has a strong shape.\nAdd more blocks next."));
    }

    private JudgeVerdict verdict(String comment) {
        return new JudgeVerdict(
                "Professor Fixture", "The build was reviewed.", SCORES, comment);
    }
}
