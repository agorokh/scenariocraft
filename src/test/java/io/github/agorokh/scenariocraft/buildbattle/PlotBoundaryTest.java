package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotBoundaryTest {
    @Test
    void derivesBorderAndYCapFromAnOddSizedPlot() {
        PlotBoundary boundary =
                PlotBoundary.forPlot(new PlotBounds(-16, 16, 48, 80), -61, 30);

        assertEquals(0.5, boundary.borderCenterX());
        assertEquals(64.5, boundary.borderCenterZ());
        assertEquals(33.0, boundary.borderSize());
        assertEquals(-60, boundary.minBuildY());
        assertEquals(-31, boundary.maxBuildY());
        assertEquals(-30, boundary.capY());
    }

    @Test
    void derivesAnExactHalfBlockCenterForAnEvenSizedPlot() {
        PlotBoundary boundary =
                PlotBoundary.forPlot(new PlotBounds(10, 11, -4, -3), 20, 5);

        assertEquals(11.0, boundary.borderCenterX());
        assertEquals(-3.0, boundary.borderCenterZ());
        assertEquals(2.0, boundary.borderSize());
    }

    @Test
    void editableVolumeIncludesPlotEdgesButExcludesFloorAndCap() {
        PlotBoundary boundary =
                PlotBoundary.forPlot(new PlotBounds(10, 12, 20, 22), 4, 3);

        assertTrue(boundary.containsEditableBlock(10, 5, 20));
        assertTrue(boundary.containsEditableBlock(12, 7, 22));
        assertFalse(boundary.containsEditableBlock(9, 5, 20));
        assertFalse(boundary.containsEditableBlock(10, 4, 20));
        assertFalse(boundary.containsEditableBlock(10, 8, 20));
        assertFalse(boundary.containsEditableBlock(10, 5, 23));
    }

    @Test
    void rejectsGeometryThatCannotUseOneSquareWorldBorder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlotBoundary.forPlot(new PlotBounds(0, 2, 0, 3), 0, 4));
    }
}
