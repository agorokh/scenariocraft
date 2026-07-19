package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlotGeometryTest {
    @Test
    void twoDefaultPlotsHaveExpectedBoundsAndSpacing() {
        List<PlotBounds> plots = PlotGeometry.aroundHub(0, 0, 2, 33, 64);

        assertEquals(
                List.of(
                        new PlotBounds(-16, 16, -80, -48),
                        new PlotBounds(-16, 16, 48, 80)),
                plots);
        assertEquals(128, plots.get(1).centerZ() - plots.get(0).centerZ());
    }

    @Test
    void eightPlotsKeepConfiguredSizeAndNeverOverlap() {
        List<PlotBounds> plots = PlotGeometry.aroundHub(120, -75, 8, 33, 64);

        assertEquals(8, plots.size());
        assertEquals(
                Set.of(
                        "120,-139",
                        "184,-139",
                        "184,-75",
                        "184,-11",
                        "120,-11",
                        "56,-11",
                        "56,-75",
                        "56,-139"),
                plots.stream()
                        .map(plot -> plot.centerX() + "," + plot.centerZ())
                        .collect(Collectors.toSet()));
        for (int first = 0; first < plots.size(); first++) {
            PlotBounds plot = plots.get(first);
            assertEquals(33, plot.width());
            assertEquals(33, plot.depth());
            assertFalse(plot.minX() <= 120
                    && plot.maxX() >= 120
                    && plot.minZ() <= -75
                    && plot.maxZ() >= -75);
            for (int second = first + 1; second < plots.size(); second++) {
                assertFalse(plot.overlaps(plots.get(second)));
            }
        }
    }

    @Test
    void spacingMustLeaveRoomForWalls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlotGeometry.aroundHub(0, 0, 2, 33, 34));
    }
}
