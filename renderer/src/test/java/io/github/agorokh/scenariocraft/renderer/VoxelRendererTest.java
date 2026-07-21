package io.github.agorokh.scenariocraft.renderer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoxelRendererTest {
    private static final String[] OUTPUTS = {
        "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
        "plan.png", "cut-x.png", "cut-z.png"
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersExactlySevenFixedSizeImagesStandalone() throws Exception {
        Path output = temporaryDirectory.resolve("worked-example");
        VoxelPlot plot = VoxelPlot.read(fixture("worked-example.voxels.json"));

        new VoxelRenderer().render(plot, output);

        try (var files = Files.list(output)) {
            assertEquals(7, files.count());
        }
        for (String name : OUTPUTS) {
            BufferedImage image = ImageIO.read(output.resolve(name).toFile());
            assertEquals(1024, image.getWidth());
            assertEquals(1024, image.getHeight());
        }
    }

    @Test
    void rejectsUnexpectedFilesInsteadOfLeavingMoreThanSevenOutputs() throws Exception {
        Path output = temporaryDirectory.resolve("reused");
        Files.createDirectories(output);
        Files.writeString(output.resolve("stale.png"), "not a renderer output");
        VoxelPlot plot = VoxelPlot.read(fixture("worked-example.voxels.json"));

        assertThrows(IOException.class, () -> new VoxelRenderer().render(plot, output));
    }

    @Test
    void renderingSameInputTwiceProducesByteIdenticalPngs() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("small-house.voxels.json"));
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");

        VoxelRenderer renderer = new VoxelRenderer();
        renderer.render(plot, first);
        renderer.render(plot, second);

        for (String name : OUTPUTS) {
            assertArrayEquals(Files.readAllBytes(first.resolve(name)),
                    Files.readAllBytes(second.resolve(name)), name);
        }
    }

    @Test
    void houseMatchesCommittedGoldenImage() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("small-house.voxels.json"));
        Path output = temporaryDirectory.resolve("golden");

        new VoxelRenderer().render(plot, output);

        Path golden = repoRoot().resolve("src/test/resources/golden/small-house-iso-ne.png");
        assertArrayEquals(Files.readAllBytes(golden),
                Files.readAllBytes(output.resolve("iso-ne.png")));
    }

    @Test
    void howToPlayImagesMatchCommittedVoxelFixture() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("speed-build-candy-volcano.voxels.json"));
        Path output = temporaryDirectory.resolve("how-to-play");

        new VoxelRenderer().render(plot, output);

        assertSiteImageMatches(output, "iso-ne.png", "candy-volcano-iso.png");
        assertSiteImageMatches(output, "cut-z.png", "candy-volcano-cutaway.png");
        assertSiteImageMatches(output, "plan.png", "candy-volcano-plan.png");
    }

    @Test
    void emptyPlotProducesSevenTransparentImages() throws Exception {
        VoxelPlot plot = VoxelPlot.read(fixture("empty-plot.voxels.json"));
        Path output = temporaryDirectory.resolve("empty");

        new VoxelRenderer().render(plot, output);

        for (String name : OUTPUTS) {
            BufferedImage image = ImageIO.read(output.resolve(name).toFile());
            assertEquals(0, image.getRGB(512, 512));
        }
    }

    @Test
    void rendersMaximumPlotInUnderFiveSeconds() throws Exception {
        int[] blocks = new int[33 * 40 * 33];
        Arrays.fill(blocks, 1);
        Path input = temporaryDirectory.resolve("maximum.voxels.json");
        Files.writeString(input, new Gson().toJson(new PerformanceDocument(
                1, "maximum", new int[] {0, 0, 0}, new int[] {33, 40, 33},
                new String[] {"minecraft:air", "minecraft:stone"}, blocks)));
        VoxelPlot plot = VoxelPlot.read(input);

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> new VoxelRenderer().render(plot, temporaryDirectory.resolve("maximum")));
    }

    @Test
    void runtimeDoesNotContainBukkit() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.Bukkit"));
    }

    private Path fixture(String name) {
        return repoRoot().resolve("src/test/resources/fixtures").resolve(name);
    }

    private void assertSiteImageMatches(Path output, String renderedName, String siteName)
            throws IOException {
        Path committed = repoRoot().resolve("site/assets/scenes").resolve(siteName);
        assertArrayEquals(Files.readAllBytes(committed),
                Files.readAllBytes(output.resolve(renderedName)), siteName);
    }

    private Path repoRoot() {
        return Path.of(System.getProperty("scenariocraft.repoRoot"));
    }

    private record PerformanceDocument(int schema, String plot_id, int[] origin, int[] size,
                                       String[] palette, int[] blocks) {}
}
