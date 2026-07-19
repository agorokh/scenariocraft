package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundTimerTest {
    @Test
    void timerArithmeticCountsExactlyToZeroAndClampsThere() {
        RoundTimer timer = RoundTimer.start(3);

        assertEquals(3, timer.remainingSeconds());
        assertEquals(1.0, timer.remainingFraction());
        assertFalse(timer.isComplete());

        timer = timer.tick();
        assertEquals(2, timer.remainingSeconds());
        assertEquals(2.0 / 3.0, timer.remainingFraction());

        timer = timer.tick().tick();
        assertEquals(0, timer.remainingSeconds());
        assertEquals(0.0, timer.remainingFraction());
        assertTrue(timer.isComplete());
        assertEquals(timer, timer.tick());
    }

    @Test
    void buildWarningsFireOnceAtEveryRequestedBoundary() {
        RoundTimer timer = RoundTimer.start(601);
        List<Integer> warnedAt = new ArrayList<>();

        while (!timer.isComplete()) {
            timer = timer.tick();
            if (timer.buildWarning().isPresent()) {
                warnedAt.add(timer.remainingSeconds());
            }
        }

        assertEquals(List.of(600, 300, 60, 10), warnedAt);
    }

    @Test
    void shortCountdownUsesTensAndLastFiveSeconds() {
        RoundTimer timer = RoundTimer.start(31);
        List<Integer> announcedAt = new ArrayList<>();

        while (!timer.isComplete()) {
            timer = timer.tick();
            if (timer.shouldAnnounceShortCountdown()) {
                announcedAt.add(timer.remainingSeconds());
            }
        }

        assertEquals(List.of(30, 20, 10, 5, 4, 3, 2, 1), announcedAt);
    }

    @Test
    void invalidDurationsAndElapsedValuesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> RoundTimer.start(0));
        assertThrows(IllegalArgumentException.class, () -> new RoundTimer(10, -1));
        assertThrows(IllegalArgumentException.class, () -> new RoundTimer(10, 11));
    }
}
