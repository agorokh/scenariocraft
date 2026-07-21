package io.github.agorokh.scenariocraft.judge;

import io.github.agorokh.scenariocraft.renderer.VoxelPlot;
import io.github.agorokh.scenariocraft.renderer.VoxelRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class RoundImages {
    static final long MAX_VOXEL_BYTES = 16L * 1024 * 1024;
    static final long MAX_TOTAL_IMAGE_BYTES = 16L * 1024 * 1024;
    static final List<String> NAMES = List.of(
            "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
            "plan.png", "cut-x.png", "cut-z.png");

    private RoundImages() {}

    static List<JudgeImage> prepare(Path roundDirectory, JudgeRound.Plot plot) throws IOException {
        String plotId = plot.plotId();
        Path roundRoot = roundDirectory.toRealPath();
        Path outputRoot = roundDirectory.resolve("out");
        Path outputDirectory = outputRoot.resolve(plotId);
        rejectSymbolicLink(outputRoot);
        rejectSymbolicLink(outputDirectory);
        List<Path> images = imagePaths(outputDirectory);
        rejectSymbolicLinks(images);
        if (allRegularFiles(images)) {
            return snapshotImages(roundRoot, images, true);
        }

        Path voxelFile = roundDirectory.resolve(plotId + ".voxels.json");
        byte[] voxelBytes = snapshotVoxelSource(roundRoot, voxelFile, plotId);
        Path temporaryOutput = Files.createTempDirectory("scenariocraft-judge-render-");
        Throwable primaryFailure = null;
        try {
            Path stableVoxelFile = temporaryOutput.resolve("input")
                    .resolve(plotId + ".voxels.json");
            Files.createDirectories(stableVoxelFile.getParent());
            Files.write(stableVoxelFile, voxelBytes);
            VoxelPlot voxelPlot = VoxelPlot.read(stableVoxelFile);
            if (!plotId.equals(voxelPlot.plotId())) {
                throw new IOException("Voxel plot_id does not match manifest plot " + plotId);
            }
            if (!plot.origin().equals(List.of(
                    voxelPlot.originX(), voxelPlot.originY(), voxelPlot.originZ()))
                    || !plot.size().equals(List.of(
                            voxelPlot.sizeX(), voxelPlot.sizeY(), voxelPlot.sizeZ()))) {
                throw new IOException(
                        "Voxel origin or size does not match manifest plot " + plotId);
            }
            Path renderedOutput = temporaryOutput.resolve("output");
            new VoxelRenderer().render(voxelPlot, renderedOutput);
            List<Path> rendered = imagePaths(renderedOutput);
            if (!allRegularFiles(rendered)) {
                throw new IOException("Renderer did not produce seven PNGs for " + plotId);
            }
            return snapshotImages(renderedOutput.toRealPath(), rendered, false);
        } catch (IOException | RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            cleanupTemporaryOutput(temporaryOutput, primaryFailure);
        }
    }

    private static List<Path> imagePaths(Path outputDirectory) {
        List<Path> images = new ArrayList<>(NAMES.size());
        for (String name : NAMES) {
            images.add(outputDirectory.resolve(name));
        }
        return images;
    }

    private static List<JudgeImage> snapshotImages(
            Path allowedRoot, List<Path> images, boolean untrustedInputs)
            throws IOException {
        long totalBytes = 0;
        for (Path image : images) {
            totalBytes = Math.addExact(totalBytes, Files.size(image));
            if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
                throw new IOException("Judge image set exceeds the aggregate byte limit");
            }
        }
        List<JudgeImage> snapshots = new ArrayList<>(images.size());
        for (Path image : images) {
            snapshots.add(untrustedInputs
                    ? JudgeImage.read(image, allowedRoot)
                    : JudgeImage.readGenerated(image, allowedRoot));
        }
        return List.copyOf(snapshots);
    }

    private static boolean allRegularFiles(List<Path> images) {
        return images.stream()
                .allMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static byte[] snapshotVoxelSource(Path roundRoot, Path voxelFile, String plotId)
            throws IOException {
        BasicFileAttributes before;
        try {
            before = Files.readAttributes(
                    voxelFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            throw new IOException(
                    "Missing seven PNGs and voxel source for " + plotId, exception);
        }
        if (!before.isRegularFile()) {
            throw new IOException("Missing seven PNGs and voxel source for " + plotId);
        }
        Path realBefore = voxelFile.toRealPath();
        if (!realBefore.startsWith(roundRoot)) {
            throw new IOException("Voxel source escapes the round directory: " + voxelFile);
        }
        JudgeImage.requireSingleLink(voxelFile);
        if (before.size() <= 0 || before.size() > MAX_VOXEL_BYTES) {
            throw new IOException("Voxel source size is outside the allowed range: " + voxelFile);
        }
        byte[] bytes;
        try (var input = Files.newInputStream(voxelFile, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) MAX_VOXEL_BYTES + 1);
        }
        BasicFileAttributes after = Files.readAttributes(
                voxelFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path realAfter = voxelFile.toRealPath();
        JudgeImage.requireSingleLink(voxelFile);
        if (bytes.length > MAX_VOXEL_BYTES
                || before.fileKey() == null
                || !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size()
                || bytes.length != before.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !realBefore.equals(realAfter)) {
            throw new IOException("Voxel source changed while it was being read: " + voxelFile);
        }
        return bytes;
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

    private static void cleanupTemporaryOutput(Path root, Throwable primaryFailure)
            throws IOException {
        try {
            deleteTree(root);
        } catch (IOException cleanupFailure) {
            if (primaryFailure == null) {
                throw cleanupFailure;
            }
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }
}
