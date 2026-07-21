package io.github.agorokh.scenariocraft.judge;

import io.github.agorokh.scenariocraft.renderer.VoxelPlot;
import io.github.agorokh.scenariocraft.renderer.VoxelRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RoundImages {
    static final List<String> NAMES = List.of(
            "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
            "plan.png", "cut-x.png", "cut-z.png");

    private RoundImages() {}

    static List<JudgeImage> prepare(Path roundDirectory, String plotId) throws IOException {
        Path roundRoot = roundDirectory.toRealPath();
        Path outputRoot = roundDirectory.resolve("out");
        Path outputDirectory = outputRoot.resolve(plotId);
        rejectSymbolicLink(outputRoot);
        rejectSymbolicLink(outputDirectory);
        List<Path> images = imagePaths(outputDirectory);
        rejectSymbolicLinks(images);
        if (allRegularFiles(images)) {
            return snapshotImages(roundRoot, images);
        }

        Path voxelFile = roundDirectory.resolve(plotId + ".voxels.json");
        validateVoxelSource(roundRoot, voxelFile, plotId);
        VoxelPlot voxelPlot = VoxelPlot.read(voxelFile);
        if (!plotId.equals(voxelPlot.plotId())) {
            throw new IOException("Voxel plot_id does not match manifest plot " + plotId);
        }

        Path temporaryOutput = Files.createTempDirectory("scenariocraft-judge-render-");
        try {
            new VoxelRenderer().render(voxelPlot, temporaryOutput);
            List<Path> rendered = imagePaths(temporaryOutput);
            if (!allRegularFiles(rendered)) {
                throw new IOException("Renderer did not produce seven PNGs for " + plotId);
            }
            return snapshotImages(temporaryOutput.toRealPath(), rendered);
        } finally {
            deleteTree(temporaryOutput);
        }
    }

    private static List<Path> imagePaths(Path outputDirectory) {
        List<Path> images = new ArrayList<>(NAMES.size());
        for (String name : NAMES) {
            images.add(outputDirectory.resolve(name));
        }
        return images;
    }

    private static List<JudgeImage> snapshotImages(Path allowedRoot, List<Path> images)
            throws IOException {
        List<JudgeImage> snapshots = new ArrayList<>(images.size());
        for (Path image : images) {
            snapshots.add(JudgeImage.read(image, allowedRoot));
        }
        return List.copyOf(snapshots);
    }

    private static boolean allRegularFiles(List<Path> images) {
        return images.stream()
                .allMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static void validateVoxelSource(Path roundRoot, Path voxelFile, String plotId)
            throws IOException {
        if (!Files.isRegularFile(voxelFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing seven PNGs and voxel source for " + plotId);
        }
        if (!voxelFile.toRealPath().startsWith(roundRoot)) {
            throw new IOException("Voxel source escapes the round directory: " + voxelFile);
        }
        JudgeImage.requireSingleLink(voxelFile);
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

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
