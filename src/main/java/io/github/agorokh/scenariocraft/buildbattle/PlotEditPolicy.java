package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;

/** Pure phase and geometry policy for contestant block edits. */
public final class PlotEditPolicy {
    private PlotEditPolicy() {}

    public static boolean mayEdit(
            RoundPhase phase, PlotBoundary boundary, int blockX, int blockY, int blockZ) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(boundary, "boundary");
        return phase == RoundPhase.BUILDING
                && boundary.containsEditableBlock(blockX, blockY, blockZ);
    }
}
