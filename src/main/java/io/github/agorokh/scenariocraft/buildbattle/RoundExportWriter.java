package io.github.agorokh.scenariocraft.buildbattle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Encodes immutable snapshots and atomically publishes complete round directories. */
final class RoundExportWriter {
    private static final Gson JSON =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path roundsDirectory;
    private final DirectoryPublisher publisher;

    RoundExportWriter(Path roundsDirectory) {
        this(roundsDirectory, RoundExportWriter::publishAtomically);
    }

    RoundExportWriter(Path roundsDirectory, DirectoryPublisher publisher) {
        this.roundsDirectory = Objects.requireNonNull(roundsDirectory, "roundsDirectory");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    Path write(RoundSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.roundId().matches("round-[0-9]{8}-[0-9]{6}")) {
            throw new IllegalArgumentException("round id must match round-<yyyymmdd>-<hhmmss>");
        }
        Files.createDirectories(roundsDirectory);
        Path finalDirectory = roundsDirectory.resolve(snapshot.roundId());
        if (Files.exists(finalDirectory)) {
            throw new IOException("round directory already exists: " + finalDirectory);
        }
        Path temporaryDirectory =
                Files.createTempDirectory(roundsDirectory, "." + snapshot.roundId() + "-");
        boolean published = false;
        try {
            List<RoundManifest.Plot> manifestPlots = new ArrayList<>(snapshot.plots().size());
            for (RoundSnapshot.Plot plot : snapshot.plots()) {
                VoxelFile voxels = VoxelCodec.encode(VoxelCodec.trimAirAbove(plot));
                manifestPlots.add(
                        new RoundManifest.Plot(
                                voxels.plotId(),
                                plot.metadata().player(),
                                voxels.origin(),
                                voxels.size()));
                writeJson(temporaryDirectory.resolve(voxels.plotId() + ".voxels.json"), voxels);
            }
            RoundManifest manifest =
                    new RoundManifest(
                            RoundManifest.SCHEMA_VERSION,
                            snapshot.roundId(),
                            snapshot.task(),
                            snapshot.world(),
                            manifestPlots);
            writeJson(temporaryDirectory.resolve("manifest.json"), manifest);
            publisher.publish(temporaryDirectory, finalDirectory);
            published = true;
            return finalDirectory;
        } finally {
            if (!published) {
                deleteTemporaryDirectory(temporaryDirectory);
            }
        }
    }

    static Gson json() {
        return JSON;
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Files.writeString(
                path, JSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void publishAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    interface DirectoryPublisher {
        void publish(Path source, Path target) throws IOException;
    }
}
