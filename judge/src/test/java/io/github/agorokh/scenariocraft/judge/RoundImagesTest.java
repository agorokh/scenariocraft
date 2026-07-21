package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.agorokh.scenariocraft.renderer.VoxelPlot;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundImagesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesRendererWhenCompletePngSetDoesNotExist() throws Exception {
        Files.copy(
                Path.of(System.getProperty("scenariocraft.repoRoot"))
                        .resolve("src/test/resources/fixtures/worked-example.voxels.json"),
                temporaryDirectory.resolve("p1.voxels.json"),
                StandardCopyOption.REPLACE_EXISTING);

        List<JudgeImage> images = RoundImages.prepare(temporaryDirectory, plot());

        assertEquals(7, images.size());
        assertTrue(images.stream().allMatch(image -> image.bytes().length > 24));
    }

    @Test
    void missingImagesAndVoxelSourceUseTheOperatorDiagnostic() {
        IOException exception = assertThrows(
                IOException.class,
                () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains(
                "Missing seven PNGs and voxel source for p1"));
    }

    @Test
    void rejectsSymbolicLinkImagesInsteadOfUploadingTheirTargets() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        Path target = Files.write(temporaryDirectory.resolve("outside.png"), new byte[] {1});
        Files.createSymbolicLink(output.resolve("iso-ne.png"), target);

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("Symbolic links are not allowed"));
    }

    @Test
    void rejectsFallbackVoxelsOwnedByAnotherPlot() throws Exception {
        String voxels = Files.readString(workedExample()).replace(
                "\"plot_id\": \"p1\"", "\"plot_id\": \"p2\"");
        Files.writeString(temporaryDirectory.resolve("p1.voxels.json"), voxels);

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("does not match manifest plot p1"));
    }

    @Test
    void rejectsHardLinkedImagesInsteadOfUploadingExternalFileContents() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        Path target = Files.write(temporaryDirectory.resolve("outside.png"), new byte[] {1});
        Files.createLink(output.resolve("iso-ne.png"), target);
        for (String name : RoundImages.NAMES) {
            if (!"iso-ne.png".equals(name)) {
                Files.write(output.resolve(name), new byte[] {1});
            }
        }

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("Hard-linked judge inputs are not allowed"));
    }

    @Test
    void rejectsHardLinkedVoxelFallbackSources() throws Exception {
        Path external = Files.copy(
                workedExample(),
                temporaryDirectory.resolve("external.voxels.json"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.createLink(temporaryDirectory.resolve("p1.voxels.json"), external);

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("Hard-linked judge inputs are not allowed"));
    }

    @Test
    void rejectsOversizedVoxelFallbackBeforeParsing() throws Exception {
        Path oversized = temporaryDirectory.resolve("p1.voxels.json");
        try (FileChannel channel = FileChannel.open(
                oversized, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(RoundImages.MAX_VOXEL_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("outside the allowed range"));
    }

    @Test
    void rejectsVoxelArraysDuringStreamingPreflight() throws Exception {
        String palettes = "\"minecraft:air\",".repeat(VoxelPlot.MAX_PALETTE_ENTRIES)
                + "\"minecraft:stone\"";
        Files.writeString(temporaryDirectory.resolve("p1.voxels.json"), """
                {"schema":1,"plot_id":"p1","origin":[100,64,200],"size":[0,0,0],
                 "palette":[%s],"blocks":[]}
                """.formatted(palettes));

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("palette exceeds the entry limit"));
    }

    @Test
    void rejectsOversizedPaletteEntriesDuringStreamingPreflight() throws Exception {
        Files.writeString(temporaryDirectory.resolve("p1.voxels.json"), """
                {"schema":1,"plot_id":"p1","origin":[100,64,200],"size":[0,0,0],
                 "palette":["minecraft:air","minecraft:%s"],"blocks":[]}
                """.formatted("a".repeat(VoxelPlot.MAX_PALETTE_ENTRY_LENGTH)));

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("palette entry exceeds the length limit"));
    }

    @Test
    void dryRunPreparationRejectsCorruptPngSets() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        for (String name : RoundImages.NAMES) {
            Files.write(output.resolve(name), new byte[] {1, 2, 3});
        }

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("valid PNG"));
    }

    @Test
    void rejectsImageSetsAboveTheAggregateRequestLimit() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        for (String name : RoundImages.NAMES) {
            Path image = output.resolve(name);
            try (FileChannel channel = FileChannel.open(
                    image, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                channel.position(3L * 1024 * 1024);
                channel.write(ByteBuffer.wrap(new byte[] {0}));
            }
        }

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, plot()));

        assertTrue(exception.getMessage().contains("aggregate byte limit"));
    }

    @Test
    void rejectsFallbackVoxelsFromAnotherArenaGeometry() throws Exception {
        Files.copy(
                workedExample(),
                temporaryDirectory.resolve("p1.voxels.json"),
                StandardCopyOption.REPLACE_EXISTING);
        JudgeRound.Plot wrongGeometry = new JudgeRound.Plot(
                "p1", "Alex", List.of(0, 64, 0), List.of(2, 2, 2));

        IOException exception = assertThrows(
                IOException.class,
                () -> RoundImages.prepare(temporaryDirectory, wrongGeometry));

        assertTrue(exception.getMessage().contains("origin or size does not match"));
    }

    private Path workedExample() {
        return Path.of(System.getProperty("scenariocraft.repoRoot"))
                .resolve("src/test/resources/fixtures/worked-example.voxels.json");
    }

    private JudgeRound.Plot plot() {
        return new JudgeRound.Plot(
                "p1", "Alex", List.of(100, 64, 200), List.of(2, 2, 2));
    }
}
