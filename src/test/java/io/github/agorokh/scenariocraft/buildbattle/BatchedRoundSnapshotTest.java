package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BatchedRoundSnapshotTest {
    @Test
    void fullPlotSnapshotHonorsEveryTickBudgetWithoutAWatchdogStall() {
        RoundExportRequest.Plot plot =
                new RoundExportRequest.Plot("p1", "KidAva", 100, 64, 200, 33, 40, 33);
        BatchedRoundSnapshot snapshot =
                new BatchedRoundSnapshot(
                        "round-20260720-204209",
                        new RoundExportRequest(
                                "Pirate ship", "battle_world", List.of(plot)));
        AtomicInteger reads = new AtomicInteger();

        assertTimeout(
                Duration.ofSeconds(1),
                () -> {
                    int ticks = 0;
                    while (!snapshot.isComplete()) {
                        int before = reads.get();
                        int completed =
                                snapshot.runBatch(
                                        (x, y, z) -> {
                                            reads.incrementAndGet();
                                            return x == 132 && y == 103 && z == 232
                                                    ? "minecraft:oak_planks"
                                                    : "minecraft:air";
                                        },
                                        4_000);
                        assertTrue(completed <= 4_000);
                        assertEquals(completed, reads.get() - before);
                        ticks++;
                    }
                    assertEquals(11, ticks);
                });

        assertEquals(33L * 40L * 33L, reads.get());
        VoxelFile voxels = VoxelCodec.encode(snapshot.finish().plots().getFirst());
        assertEquals(List.of(33, 40, 33), voxels.size());
        assertEquals(33 * 40 * 33, voxels.blocks().size());
    }
}
