package io.github.agorokh.scenariocraft.buildbattle;

/** Configuration-backed durations consumed by the Speed Build round controller. */
public record PhaseTimings(
        int gatherSeconds, int noteSeconds, int buildSeconds, int revealLingerSeconds) {
    public PhaseTimings {
        if (gatherSeconds <= 0
                || noteSeconds <= 0
                || buildSeconds <= 0
                || revealLingerSeconds <= 0) {
            throw new IllegalArgumentException("phase timings must be positive");
        }
    }
}
