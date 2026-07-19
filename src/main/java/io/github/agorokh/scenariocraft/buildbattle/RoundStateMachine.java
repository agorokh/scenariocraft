package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;

/** Small mutable owner for the validated phase graph. */
public final class RoundStateMachine {
    private RoundPhase phase = RoundPhase.IDLE;

    public RoundPhase phase() {
        return phase;
    }

    public void transitionTo(RoundPhase next) {
        Objects.requireNonNull(next, "next");
        if (!phase.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "illegal round phase transition: " + phase + " -> " + next);
        }
        phase = next;
    }
}
