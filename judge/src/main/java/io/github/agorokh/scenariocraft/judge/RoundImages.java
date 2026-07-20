package io.github.agorokh.scenariocraft.judge;

import io.github.agorokh.scenariocraft.renderer.VoxelPlot;
import io.github.agorokh.scenariocraft.renderer.VoxelRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RoundImages {
    static final List<String> NAMES = List.of(
            "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
            "plan.png", "cut-x.png", "cut-z.png");

    private RoundImages() {}

    static List<Path> prepare(Path roundDirectory, String plotId) throws IOException {
        Path outputDirectory = roundDirectory.resolve("out").resolve(plotId);
        List<Path> images = imagePaths(outputDirectory);
        if (!allRegularFiles(images)) {
            Path voxelFile = roundDirectory.resolve(plotId + ".voxels.json");
            if (!Files.isRegularFile(voxelFile)) {
                throw new IOException("Missing seven PNGs and voxel source for " + plotId);
            }
            new VoxelRenderer().render(VoxelPlot.read(voxelFile), outputDirectory);
            images = imagePaths(outputDirectory);
        }
        if (!allRegularFiles(images)) {
            throw new IOException("Renderer did not produce seven PNGs for " + plotId);
        }
        return List.copyOf(images);
    }

    private static List<Path> imagePaths(Path outputDirectory) {
        List<Path> images = new ArrayList<>(NAMES.size());
        for (String name : NAMES) {
            images.add(outputDirectory.resolve(name));
        }
        return images;
    }

    private static boolean allRegularFiles(List<Path> images) {
        return images.stream().allMatch(Files::isRegularFile);
    }
}
