package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Map;
import java.util.Optional;

/** Immutable whole-second countdown arithmetic shared by phases and the bossbar. */
public record RoundTimer(int durationSeconds, int elapsedSeconds) {
    private static final Map<Integer, String> BUILD_WARNINGS =
            Map.of(
                    600, "10 minutes left — keep those great ideas growing!",
                    300, "5 minutes left — your build is taking shape!",
                    60, "1 minute left — add your favorite finishing touches!",
                    10, "10 seconds left — make them count!");

    public RoundTimer {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (elapsedSeconds < 0 || elapsedSeconds > durationSeconds) {
            throw new IllegalArgumentException(
                    "elapsedSeconds must be between zero and the duration");
        }
    }

    public static RoundTimer start(int durationSeconds) {
        return new RoundTimer(durationSeconds, 0);
    }

    public RoundTimer tick() {
        return isComplete() ? this : new RoundTimer(durationSeconds, elapsedSeconds + 1);
    }

    public int remainingSeconds() {
        return durationSeconds - elapsedSeconds;
    }

    public double remainingFraction() {
        return (double) remainingSeconds() / durationSeconds;
    }

    public boolean isComplete() {
        return elapsedSeconds == durationSeconds;
    }

    public Optional<String> buildWarning() {
        return Optional.ofNullable(BUILD_WARNINGS.get(remainingSeconds()));
    }

    public boolean shouldAnnounceShortCountdown() {
        int remaining = remainingSeconds();
        return remaining > 0 && (remaining <= 5 || remaining % 10 == 0);
    }
}
