package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

        List<JudgeImage> images = RoundImages.prepare(temporaryDirectory, "p1");

        assertEquals(7, images.size());
        assertTrue(images.stream().allMatch(image -> image.bytes().length > 24));
    }

    @Test
    void rejectsSymbolicLinkImagesInsteadOfUploadingTheirTargets() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        Path target = Files.write(temporaryDirectory.resolve("outside.png"), new byte[] {1});
        Files.createSymbolicLink(output.resolve("iso-ne.png"), target);

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, "p1"));

        assertTrue(exception.getMessage().contains("Symbolic links are not allowed"));
    }

    @Test
    void rejectsFallbackVoxelsOwnedByAnotherPlot() throws Exception {
        String voxels = Files.readString(workedExample()).replace(
                "\"plot_id\": \"p1\"", "\"plot_id\": \"p2\"");
        Files.writeString(temporaryDirectory.resolve("p1.voxels.json"), voxels);

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, "p1"));

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
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, "p1"));

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
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, "p1"));

        assertTrue(exception.getMessage().contains("Hard-linked judge inputs are not allowed"));
    }

    @Test
    void dryRunPreparationRejectsCorruptPngSets() throws Exception {
        Path output = Files.createDirectories(temporaryDirectory.resolve("out/p1"));
        for (String name : RoundImages.NAMES) {
            Files.write(output.resolve(name), new byte[] {1, 2, 3});
        }

        IOException exception = assertThrows(
                IOException.class, () -> RoundImages.prepare(temporaryDirectory, "p1"));

        assertTrue(exception.getMessage().contains("not a valid PNG"));
    }

    private Path workedExample() {
        return Path.of(System.getProperty("scenariocraft.repoRoot"))
                .resolve("src/test/resources/fixtures/worked-example.voxels.json");
    }
}
