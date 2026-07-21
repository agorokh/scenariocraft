package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DemoSampleBuildTest {
    @Test
    void parsesAndPlacesResourceFillsRelativeToThePlotCenter() throws Exception {
        DemoSampleBuild sample = sample("STONE -1 1 0 0 -2 2\nSEA_LANTERN 0 0 1 3 0 0\n");

        List<BlockFill> fills = sample.placeIn(new PlotBounds(10, 20, -20, -10), 5, 8);

        assertEquals(
                new BlockFill(new Cuboid(14, 16, 6, 6, -17, -13), Material.STONE),
                fills.get(0));
        assertEquals(
                new BlockFill(new Cuboid(15, 15, 7, 9, -15, -15), Material.SEA_LANTERN),
                fills.get(1));
    }

    @Test
    void rejectsUnsafeOrOutOfBoundsSampleData() throws Exception {
        assertThrows(IOException.class, () -> sample("AIR 0 0 0 0 0 0\n"));
        assertThrows(IOException.class, () -> sample("STONE 0 nope 0 0 0 0\n"));

        DemoSampleBuild tooWide = sample("STONE -3 3 0 0 0 0\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> tooWide.placeIn(new PlotBounds(-1, 1, -1, 1), 0, 5));
    }

    private static DemoSampleBuild sample(String value) throws IOException {
        return DemoSampleBuild.load(
                new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
}
