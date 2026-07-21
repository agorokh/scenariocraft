package io.github.agorokh.scenariocraft.buildbattle;

/** Configurable polling and celebration cadence for judge results. */
public record ResultAnnouncementSettings(
        int pollTicks, int celebrationBursts, int celebrationIntervalTicks) {
    public ResultAnnouncementSettings {
        if (pollTicks < 1 || pollTicks > 1_200) {
            throw new IllegalArgumentException("results-poll-ticks must be between 1 and 1200");
        }
        if (celebrationBursts < 1 || celebrationBursts > 10) {
            throw new IllegalArgumentException("results-celebration-bursts must be between 1 and 10");
        }
        if (celebrationIntervalTicks < 1 || celebrationIntervalTicks > 100) {
            throw new IllegalArgumentException("results-celebration-interval-ticks must be between 1 and 100");
        }
    }

}
