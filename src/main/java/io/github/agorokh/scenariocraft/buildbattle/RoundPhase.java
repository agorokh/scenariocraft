package io.github.agorokh.scenariocraft.buildbattle;

import java.util.EnumSet;
import java.util.Set;

/** Explicit Build Battle phase graph, including clean abort edges back to idle. */
public enum RoundPhase {
    IDLE,
    PREPARING,
    GATHERING,
    NOTE_PICK,
    BUILDING,
    REVEAL;

    private static final Set<RoundPhase> ACTIVE_PHASES =
            EnumSet.of(PREPARING, GATHERING, NOTE_PICK, BUILDING, REVEAL);

    public boolean canTransitionTo(RoundPhase next) {
        if (next == null || next == this) {
            return false;
        }
        if (next == IDLE) {
            return ACTIVE_PHASES.contains(this);
        }
        return switch (this) {
            case IDLE -> next == PREPARING;
            case PREPARING -> next == GATHERING;
            case GATHERING -> next == NOTE_PICK;
            case NOTE_PICK -> next == BUILDING;
            case BUILDING -> next == REVEAL;
            case REVEAL -> false;
        };
    }
}
