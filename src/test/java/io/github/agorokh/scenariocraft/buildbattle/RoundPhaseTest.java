package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoundPhaseTest {
    private static final Map<RoundPhase, Set<RoundPhase>> LEGAL_TRANSITIONS =
            Map.of(
                    RoundPhase.IDLE, EnumSet.of(RoundPhase.PREPARING),
                    RoundPhase.PREPARING,
                            EnumSet.of(RoundPhase.GATHERING, RoundPhase.IDLE),
                    RoundPhase.GATHERING,
                            EnumSet.of(RoundPhase.NOTE_PICK, RoundPhase.IDLE),
                    RoundPhase.NOTE_PICK,
                            EnumSet.of(RoundPhase.BUILDING, RoundPhase.IDLE),
                    RoundPhase.BUILDING,
                            EnumSet.of(RoundPhase.REVEAL, RoundPhase.IDLE),
                    RoundPhase.REVEAL, EnumSet.of(RoundPhase.IDLE));

    @Test
    void transitionTableClassifiesEveryPhasePair() {
        for (RoundPhase current : RoundPhase.values()) {
            for (RoundPhase next : RoundPhase.values()) {
                assertEquals(
                        LEGAL_TRANSITIONS.get(current).contains(next),
                        current.canTransitionTo(next),
                        current + " -> " + next);
            }
        }
    }

    @Test
    void stateMachineRunsTheFullCycle() {
        RoundStateMachine state = new RoundStateMachine();

        state.transitionTo(RoundPhase.PREPARING);
        state.transitionTo(RoundPhase.GATHERING);
        state.transitionTo(RoundPhase.NOTE_PICK);
        state.transitionTo(RoundPhase.BUILDING);
        state.transitionTo(RoundPhase.REVEAL);
        state.transitionTo(RoundPhase.IDLE);

        assertEquals(RoundPhase.IDLE, state.phase());
    }

    @Test
    void stateMachineRejectsIllegalTransitionWithoutChangingPhase() {
        RoundStateMachine state = new RoundStateMachine();

        assertThrows(
                IllegalStateException.class,
                () -> state.transitionTo(RoundPhase.BUILDING));

        assertEquals(RoundPhase.IDLE, state.phase());
    }
}
