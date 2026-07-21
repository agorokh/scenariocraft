package io.github.agorokh.scenariocraft.buildbattle;

/** Configurable polling and celebration cadence for judge results. */
public record ResultAnnouncementSettings(
        int pollSeconds, int celebrationBursts, int celebrationIntervalTicks) {
    public ResultAnnouncementSettings {
        if (pollSeconds < 1 || pollSeconds > 60) {
            throw new IllegalArgumentException("results-poll-seconds must be between 1 and 60");
        }
        if (celebrationBursts < 1 || celebrationBursts > 10) {
            throw new IllegalArgumentException("results-celebration-bursts must be between 1 and 10");
        }
        if (celebrationIntervalTicks < 1 || celebrationIntervalTicks > 100) {
            throw new IllegalArgumentException("results-celebration-interval-ticks must be between 1 and 100");
        }
    }

    long pollTicks() {
        return Math.multiplyExact(pollSeconds, 20L);
    }
}
