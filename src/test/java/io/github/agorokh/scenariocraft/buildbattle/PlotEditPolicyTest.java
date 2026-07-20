package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlotEditPolicyTest {
    private static final PlotBoundary BOUNDARY =
            PlotBoundary.forPlot(new PlotBounds(10, 12, 20, 22), 4, 3);

    @Test
    void onlyBuildingAllowsEditsInsideTheAssignedPlot() {
        Map<RoundPhase, Boolean> expected =
                Map.of(
                        RoundPhase.IDLE, false,
                        RoundPhase.PREPARING, false,
                        RoundPhase.GATHERING, false,
                        RoundPhase.NOTE_PICK, false,
                        RoundPhase.BUILDING, true,
                        RoundPhase.REVEAL, false);

        for (RoundPhase phase : RoundPhase.values()) {
            assertEquals(
                    expected.get(phase),
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 11, 6, 21),
                    phase.toString());
        }
    }

    @Test
    void outsidePlotAndVerticalLimitEditsAreDeniedInEveryPhase() {
        for (RoundPhase phase : RoundPhase.values()) {
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 9, 6, 21),
                    phase + " west");
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 13, 6, 21),
                    phase + " east");
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 11, 6, 19),
                    phase + " north");
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 11, 6, 23),
                    phase + " south");
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 11, 4, 21),
                    phase + " floor");
            assertFalse(
                    PlotEditPolicy.mayEdit(phase, BOUNDARY, 11, 8, 21),
                    phase + " cap");
        }
    }
}
