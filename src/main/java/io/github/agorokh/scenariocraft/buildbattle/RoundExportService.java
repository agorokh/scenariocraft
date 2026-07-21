package io.github.agorokh.scenariocraft.buildbattle;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Batches main-thread world reads, then writes the immutable snapshot asynchronously. */
final class RoundExportService implements RoundExporter {
    private static final long CHUNK_PREPARATION_TIMEOUT_TICKS = 20L * 30L;
    private static final DateTimeFormatter ROUND_ID_FORMAT =
            DateTimeFormatter.ofPattern("'round-'yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final World world;
    private final int readsPerTick;
    private final Clock clock;
    private final RoundExportWriter writer;
    private final Logger logger;
    private final BukkitTask snapshotTask;
    private final Set<ChunkCoordinate> chunkTickets = new HashSet<>();
    private BatchedRoundSnapshot activeSnapshot;
    private List<CompletableFuture<Chunk>> chunkLoads = List.of();
    private BukkitTask preparationTimeoutTask;
    private long preparationGeneration;
    private boolean preparingChunks;
    private boolean writing;
    private boolean closed;

    RoundExportService(
            Plugin plugin,
            World world,
            int readsPerTick,
            Clock clock,
            RoundExportWriter writer,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.world = Objects.requireNonNull(world, "world");
        if (readsPerTick <= 0) {
            throw new IllegalArgumentException("readsPerTick must be positive");
        }
        this.readsPerTick = readsPerTick;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.snapshotTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::runSnapshotTick, 1L, 1L);
    }

    static RoundExportService forPlugin(
            Plugin plugin, World world, int readsPerTick, Logger logger) {
        Path roundsDirectory = plugin.getDataFolder().toPath().resolve("rounds");
        return new RoundExportService(
                plugin,
                world,
                readsPerTick,
                Clock.systemUTC(),
                new RoundExportWriter(roundsDirectory),
                logger);
    }

    @Override
    public synchronized String export(RoundExportRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed) {
            throw new IllegalStateException("round exporter is closed");
        }
        if (activeSnapshot != null || writing) {
            throw new IllegalStateException("a round export is already in progress");
        }
        String roundId = ROUND_ID_FORMAT.format(clock.instant());
        activeSnapshot = new BatchedRoundSnapshot(roundId, request);
        preparingChunks = true;
        prepareChunks(request, ++preparationGeneration);
        return roundId;
    }

    @Override
    public synchronized boolean isBusy() {
        return activeSnapshot != null || writing;
    }

    @Override
    public synchronized boolean isReadingArena() {
        return activeSnapshot != null;
    }

    @Override
    public synchronized void cancel() {
        preparationGeneration++;
        activeSnapshot = null;
        preparingChunks = false;
        cancelPreparationTimeout();
        cancelChunkLoads();
        releaseChunkTickets();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancel();
        snapshotTask.cancel();
    }

    private void runSnapshotTick() {
        RoundSnapshot completeSnapshot;
        synchronized (this) {
            if (closed || activeSnapshot == null || preparingChunks) {
                return;
            }
            try {
                activeSnapshot.runBatch(this::readBlockId, readsPerTick);
                if (!activeSnapshot.isComplete()) {
                    return;
                }
                completeSnapshot = activeSnapshot.finish();
                activeSnapshot = null;
                releaseChunkTickets();
                writing = true;
            } catch (RuntimeException failure) {
                failSnapshot("snapshot failed", failure);
                return;
            }
        }

        try {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(plugin, () -> writeSnapshot(completeSnapshot));
        } catch (RuntimeException failure) {
            synchronized (this) {
                writing = false;
            }
            logger.log(
                    Level.SEVERE,
                    "SCENARIOCRAFT_EXPORT_FAILURE async writer scheduling failed",
                    failure);
        }
    }

    private String readBlockId(int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        return switch (material) {
            case AIR, CAVE_AIR, VOID_AIR -> VoxelFile.AIR_BLOCK_ID;
            default -> material.getKey().toString();
        };
    }

    private void prepareChunks(RoundExportRequest request, long generation) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        try {
            for (ChunkCoordinate chunk : chunkCoordinates(request)) {
                futures.add(world.getChunkAtAsync(chunk.x(), chunk.z(), true));
                chunkLoads = List.copyOf(futures);
            }
            preparationTimeoutTask =
                    plugin.getServer()
                            .getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> timeOutChunkPreparation(generation),
                                    CHUNK_PREPARATION_TIMEOUT_TICKS);
        } catch (RuntimeException failure) {
            failSnapshot("chunk preparation could not start", failure);
            return;
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            try {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        finishChunkPreparation(
                                                                futures, failure, generation));
                            } catch (RuntimeException schedulingFailure) {
                                synchronized (this) {
                                    if (isActivePreparation(generation)) {
                                        failSnapshot(
                                                "chunk preparation handoff failed",
                                                schedulingFailure);
                                    }
                                }
                            }
                        });
    }

    private synchronized void finishChunkPreparation(
            List<CompletableFuture<Chunk>> futures, Throwable failure, long generation) {
        if (!isActivePreparation(generation)) {
            return;
        }
        cancelPreparationTimeout();
        if (failure != null) {
            failSnapshot("chunk preparation failed", failure);
            return;
        }
        try {
            for (CompletableFuture<Chunk> future : futures) {
                Chunk chunk = future.join();
                if (world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin)) {
                    chunkTickets.add(new ChunkCoordinate(chunk.getX(), chunk.getZ()));
                }
            }
            chunkLoads = List.of();
            preparingChunks = false;
        } catch (RuntimeException preparationFailure) {
            failSnapshot("chunk preparation failed", preparationFailure);
        }
    }

    private synchronized void timeOutChunkPreparation(long generation) {
        if (!isActivePreparation(generation)) {
            return;
        }
        if (chunkLoads.stream().allMatch(CompletableFuture::isDone)) {
            return;
        }
        failSnapshot(
                "chunk preparation timed out",
                new TimeoutException("round export chunk preparation exceeded 30 seconds"));
    }

    private boolean isActivePreparation(long generation) {
        return !closed
                && preparingChunks
                && activeSnapshot != null
                && preparationGeneration == generation;
    }

    private void failSnapshot(String message, Throwable failure) {
        activeSnapshot = null;
        preparingChunks = false;
        cancelPreparationTimeout();
        cancelChunkLoads();
        releaseChunkTickets();
        logger.log(Level.SEVERE, "SCENARIOCRAFT_EXPORT_FAILURE " + message, failure);
    }

    private void cancelPreparationTimeout() {
        if (preparationTimeoutTask != null) {
            preparationTimeoutTask.cancel();
            preparationTimeoutTask = null;
        }
    }

    private void cancelChunkLoads() {
        for (CompletableFuture<Chunk> chunkLoad : chunkLoads) {
            chunkLoad.cancel(true);
        }
        chunkLoads = List.of();
    }

    private void releaseChunkTickets() {
        for (ChunkCoordinate chunk : chunkTickets) {
            try {
                world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
            } catch (RuntimeException failure) {
                logger.log(
                        Level.WARNING,
                        "Could not release a round export chunk ticket",
                        failure);
            }
        }
        chunkTickets.clear();
    }

    private static List<ChunkCoordinate> chunkCoordinates(RoundExportRequest request) {
        Set<ChunkCoordinate> coordinates = new HashSet<>();
        for (RoundExportRequest.Plot plot : request.plots()) {
            int maxX = Math.addExact(plot.originX(), plot.sizeX() - 1);
            int maxZ = Math.addExact(plot.originZ(), plot.sizeZ() - 1);
            int minChunkX = Math.floorDiv(plot.originX(), 16);
            int maxChunkX = Math.floorDiv(maxX, 16);
            int minChunkZ = Math.floorDiv(plot.originZ(), 16);
            int maxChunkZ = Math.floorDiv(maxZ, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    coordinates.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        return List.copyOf(coordinates);
    }

    private void writeSnapshot(RoundSnapshot snapshot) {
        try {
            Path output = writer.write(snapshot);
            logger.info(
                    "Round export complete: "
                            + snapshot.plots().size()
                            + " plots written to "
                            + output);
        } catch (Exception failure) {
            logger.log(Level.SEVERE, "SCENARIOCRAFT_EXPORT_FAILURE write failed", failure);
        } finally {
            synchronized (this) {
                writing = false;
            }
        }
    }
}
