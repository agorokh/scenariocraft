package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ArenaFillPlanTest {
    @Test
    void defaultTwoPlotPlanClearsBeforeBuildingFourWallsAndACapPerPlot() {
        List<PlotBounds> plots = PlotGeometry.aroundHub(0, 0, 2, 33, 64);

        ArenaFillPlan plan =
                ArenaFillPlan.forPlots(
                        plots, -61, 30, new SecretChestPosition(2, -60, 0));

        assertEquals(13, plan.fills().size());
        assertEquals(86_561, plan.totalBlockMutations());
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -60, -30, -81, -47), Material.AIR),
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
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -30, -30, -81, -47),
                        Material.BARRIER),
                plan.fills().get(5));
        assertEquals(Material.AIR, plan.fills().get(6).material());
        assertEquals(
                new BlockFill(new Cuboid(2, 2, -60, -60, 0, 0), Material.CHEST),
                plan.fills().getLast());
    }

    @Test
    void listsEveryTouchedChunkIncludingNegativeCoordinates() {
        ArenaFillPlan plan =
                ArenaFillPlan.forPlots(
                        List.of(new PlotBounds(-17, 15, -17, 15)),
                        -61,
                        30,
                        new SecretChestPosition(2, -60, 0));

        assertEquals(16, plan.chunkCoordinates().size());
        assertEquals(
                Set.of(
                        new ChunkCoordinate(-2, -2),
                        new ChunkCoordinate(-2, -1),
                        new ChunkCoordinate(-2, 0),
                        new ChunkCoordinate(-2, 1),
                        new ChunkCoordinate(-1, -2),
                        new ChunkCoordinate(-1, -1),
                        new ChunkCoordinate(-1, 0),
                        new ChunkCoordinate(-1, 1),
                        new ChunkCoordinate(0, -2),
                        new ChunkCoordinate(0, -1),
                        new ChunkCoordinate(0, 0),
                        new ChunkCoordinate(0, 1),
                        new ChunkCoordinate(1, -2),
                        new ChunkCoordinate(1, -1),
                        new ChunkCoordinate(1, 0),
                        new ChunkCoordinate(1, 1)),
                Set.copyOf(plan.chunkCoordinates()));
    }

    @Test
    void revealPlanRemovesTheFourWallsAndYCapThroughTheBudget() {
        List<PlotBounds> plots = PlotGeometry.aroundHub(0, 0, 2, 33, 64);

        ArenaFillPlan plan = ArenaFillPlan.forWallRemoval(plots, -61, 30);

        assertEquals(10, plan.fills().size());
        assertEquals(10_610, plan.totalBlockMutations());
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -60, -31, -81, -81),
                        Material.AIR),
                plan.fills().get(0));
        assertEquals(
                new BlockFill(
                        new Cuboid(17, 17, -60, -31, -80, -48),
                        Material.AIR),
                plan.fills().get(3));
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -30, -30, -81, -47),
                        Material.AIR),
                plan.fills().get(4));
        assertEquals(
                new BlockFill(
                        new Cuboid(-17, 17, -60, -31, 47, 47),
                        Material.AIR),
                plan.fills().get(5));
    }
}
