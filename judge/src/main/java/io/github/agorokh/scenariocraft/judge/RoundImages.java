package io.github.agorokh.scenariocraft.judge;

import io.github.agorokh.scenariocraft.renderer.VoxelPlot;
import io.github.agorokh.scenariocraft.renderer.VoxelRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RoundImages {
    static final List<String> NAMES = List.of(
            "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
            "plan.png", "cut-x.png", "cut-z.png");

    private RoundImages() {}

    static List<Path> prepare(Path roundDirectory, String plotId) throws IOException {
        Path outputRoot = roundDirectory.resolve("out");
        Path outputDirectory = outputRoot.resolve(plotId);
        rejectSymbolicLink(outputRoot);
        rejectSymbolicLink(outputDirectory);
        List<Path> images = imagePaths(outputDirectory);
        rejectSymbolicLinks(images);
        if (!allRegularFiles(images)) {
            Path voxelFile = roundDirectory.resolve(plotId + ".voxels.json");
            if (!Files.isRegularFile(voxelFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Missing seven PNGs and voxel source for " + plotId);
            }
            VoxelPlot voxelPlot = VoxelPlot.read(voxelFile);
            if (!plotId.equals(voxelPlot.plotId())) {
                throw new IOException("Voxel plot_id does not match manifest plot " + plotId);
            }
            new VoxelRenderer().render(voxelPlot, outputDirectory);
            images = imagePaths(outputDirectory);
            rejectSymbolicLinks(images);
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
        return images.stream()
                .allMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static void rejectSymbolicLinks(List<Path> paths) throws IOException {
        for (Path path : paths) {
            rejectSymbolicLink(path);
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symbolic links are not allowed in judge inputs: " + path);
        }
    }
}
