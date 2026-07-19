package io.github.agorokh.scenariocraft.buildbattle;

/** Configuration-backed phase durations reserved for the phase controller in BB-03. */
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
