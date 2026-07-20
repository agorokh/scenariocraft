package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        List<Path> images = RoundImages.prepare(temporaryDirectory, "p1");

        assertEquals(7, images.size());
        assertTrue(images.stream().allMatch(Files::isRegularFile));
    }
}
