package io.github.agorokh.scenariocraft.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoxelPlotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsNormativeWorkedExampleWithXFastestIndexing() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("worked-example.voxels.json"));

        assertEquals("p1", plot.plotId());
        assertEquals(100, plot.originX());
        assertEquals(64, plot.originY());
        assertEquals(200, plot.originZ());
        assertEquals("minecraft:oak_planks", plot.blockIdAt(1, 0, 0));
        assertEquals("minecraft:air", plot.blockIdAt(0, 0, 0));
        assertEquals("minecraft:air", plot.blockIdAt(1, 0, 1));
    }

    @Test
    void acceptsNormativeEmptyPlot() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("empty-plot.voxels.json"));

        assertEquals(33, plot.sizeX());
        assertEquals(0, plot.sizeY());
        assertEquals(33, plot.sizeZ());
    }

    @Test
    void rejectsBlocksWhoseLengthDoesNotMatchSize() throws Exception {
        Path input = temporaryDirectory.resolve("bad.voxels.json");
        Files.writeString(input, """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[2,2,2],
                 "palette":["minecraft:air"],"blocks":[]}
                """);

        assertThrows(IllegalArgumentException.class, () -> VoxelPlot.read(input));
    }

    @Test
    void rejectsNonIntegerJsonTokensInIntegerFields() throws Exception {
        assertInvalid("string-schema.voxels.json", """
                {"schema":"1","plot_id":"bad","origin":[0,0,0],"size":[0,0,0],
                 "palette":["minecraft:air"],"blocks":[]}
                """);
        assertInvalid("string-size.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":["0",0,0],
                 "palette":["minecraft:air"],"blocks":[]}
                """);
        assertInvalid("decimal-block.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[1,1,1],
                 "palette":["minecraft:air"],"blocks":[0.0]}
                """);
    }

    @Test
    void rejectsSizeVolumeThatOverflowsLong() throws Exception {
        assertInvalid("overflow.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],
                 "size":[4194304,4194304,4194304],
                 "palette":["minecraft:air"],"blocks":[]}
                """);
    }

    @Test
    void rejectsNullOrBlankPaletteEntries() throws Exception {
        assertInvalid("null-palette.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[0,0,0],
                 "palette":["minecraft:air",null],"blocks":[]}
                """);
        assertInvalid("blank-palette.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[0,0,0],
                 "palette":["minecraft:air","  "],"blocks":[]}
                """);
    }

    @Test
    void rejectsPaletteEntriesOutsideTheFrozenBlockIdSchema() throws Exception {
        assertInvalid("malformed-palette.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[0,0,0],
                 "palette":["minecraft:air","not a block id"],"blocks":[]}
                """);
        assertInvalid("oversized-palette.voxels.json", """
                {"schema":1,"plot_id":"bad","origin":[0,0,0],"size":[0,0,0],
                 "palette":["minecraft:air","minecraft:%s"],"blocks":[]}
                """.formatted("a".repeat(VoxelPlot.MAX_PALETTE_ENTRY_LENGTH)));
    }

    private void assertInvalid(String name, String json) throws Exception {
        Path input = temporaryDirectory.resolve(name);
        Files.writeString(input, json);
        assertThrows(IllegalArgumentException.class, () -> VoxelPlot.read(input));
    }

    private Path fixture(String name) {
        return Path.of(System.getProperty("scenariocraft.repoRoot"),
                "src/test/resources/fixtures", name);
    }
}
