package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ArenaFillPlanTest {
    @Test
    void defaultTwoPlotPlanClearsBeforeBuildingFourWallsPerPlot() {
        List<PlotBounds> plots = PlotGeometry.aroundHub(0, 0, 2, 33, 64);

        ArenaFillPlan plan = ArenaFillPlan.forPlots(plots, -61, 30);

        assertEquals(10, plan.fills().size());
        assertEquals(81_660, plan.totalBlockMutations());
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -60, -31, -81, -47), Material.AIR),
                plan.fills().get(0));
        assertEquals(
                new BlockFill(
                        new Cuboid(
                                -17,
                                17,
                                -60,
                                -31,
                                -81,
                                -81),
                        Material.WHITE_CONCRETE),
                plan.fills().get(1));
        assertEquals(
                new BlockFill(
                        new Cuboid(
                                17,
                                17,
                                -60,
                                -31,
                                -80,
                                -48),
                        Material.WHITE_CONCRETE),
                plan.fills().get(4));
        assertEquals(Material.AIR, plan.fills().get(5).material());
    }
}
