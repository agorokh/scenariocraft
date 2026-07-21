package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScoresTest {
    @Test
    void personaScoreIsMeanOfFourSharedCriteria() {
        Scores scores = new Scores(7, 8, 6, 9);

        assertEquals(7.5, scores.mean());
    }

    @Test
    void rejectsScoresOutsideOneToTen() {
        assertThrows(IllegalArgumentException.class, () -> new Scores(0, 8, 6, 9));
        assertThrows(IllegalArgumentException.class, () -> new Scores(7, 11, 6, 9));
    }
}
