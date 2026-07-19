package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns the single per-tick queue used to clear plots and build their walls. */
public final class BatchedBlockEditor implements AutoCloseable {
    private final Plugin plugin;
    private final World world;
    private final BatchedWorkQueue queue;
    private final Logger logger;
    private final BukkitTask task;
    private final Set<ChunkCoordinate> chunkTickets = new HashSet<>();
    private Runnable completion;
    private Consumer<Throwable> preparationFailure;
    private boolean preparingChunks;
    private volatile boolean closed;

    public BatchedBlockEditor(Plugin plugin, World world, int blocksPerTick, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.world = Objects.requireNonNull(world, "world");
        this.queue = new BatchedWorkQueue(blocksPerTick);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::runTick, 1L, 1L);
    }

    public long enqueueArena(
            List<PlotBounds> plots,
            int floorY,
            int wallHeight,
            LongConsumer onComplete,
            Consumer<Throwable> onPreparationFailure) {
        if (isBusy()) {
            throw new IllegalStateException("an arena build is already queued");
        }
        ArenaFillPlan plan = ArenaFillPlan.forPlots(plots, floorY, wallHeight);
        long scheduledBlocks = plan.totalBlockMutations();
        LongConsumer completionConsumer = Objects.requireNonNull(onComplete, "onComplete");
        preparationFailure =
                Objects.requireNonNull(onPreparationFailure, "onPreparationFailure");
        completion = () -> completionConsumer.accept(scheduledBlocks);
        preparingChunks = true;
        prepareChunks(plan);
        return scheduledBlocks;
    }

    public boolean isBusy() {
        return preparingChunks || queue.hasWork();
    }

    public long pendingBlocks() {
        return queue.pendingSteps();
    }

    @Override
    public void close() {
        closed = true;
        task.cancel();
        preparingChunks = false;
        completion = null;
        preparationFailure = null;
        releaseChunkTickets();
    }

    private void runTick() {
        boolean hadWork = queue.hasWork();
        queue.runTick();
        if (hadWork && !queue.hasWork() && completion != null) {
            Runnable finished = completion;
            completion = null;
            preparationFailure = null;
            releaseChunkTickets();
            try {
                finished.run();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Arena completion callback failed", exception);
            }
        }
    }

    private void prepareChunks(ArenaFillPlan plan) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        try {
            for (ChunkCoordinate chunk : plan.chunkCoordinates()) {
                futures.add(world.getChunkAtAsync(chunk.x(), chunk.z(), true));
            }
        } catch (RuntimeException exception) {
            finishPreparation(plan, futures, exception);
            return;
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            if (closed) {
                                return;
                            }
                            try {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> finishPreparation(plan, futures, failure));
                            } catch (RuntimeException exception) {
                                if (!closed) {
                                    reportPreparationFailure(exception);
                                }
                            }
                        });
    }

    private void finishPreparation(
            ArenaFillPlan plan, List<CompletableFuture<Chunk>> futures, Throwable failure) {
        if (closed) {
            return;
        }
        if (failure != null) {
            reportPreparationFailure(failure);
            return;
        }

        try {
            for (CompletableFuture<Chunk> future : futures) {
                Chunk chunk = future.join();
                ChunkCoordinate coordinate = new ChunkCoordinate(chunk.getX(), chunk.getZ());
                if (world.addPluginChunkTicket(coordinate.x(), coordinate.z(), plugin)) {
                    chunkTickets.add(coordinate);
                }
            }
            for (BlockFill fill : plan.fills()) {
                queue.add(new BlockFillOperation(world, fill.bounds(), fill.material()));
            }
            preparingChunks = false;
        } catch (RuntimeException exception) {
            reportPreparationFailure(exception);
        }
    }

    private void reportPreparationFailure(Throwable failure) {
        Consumer<Throwable> failureConsumer = preparationFailure;
        preparingChunks = false;
        completion = null;
        preparationFailure = null;
        releaseChunkTickets();
        logger.log(Level.SEVERE, "Arena chunk preparation failed", failure);
        if (failureConsumer != null) {
            try {
                failureConsumer.accept(failure);
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Arena preparation failure callback failed", exception);
            }
        }
    }

    private void releaseChunkTickets() {
        for (ChunkCoordinate chunk : chunkTickets) {
            try {
                world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Could not release an arena chunk ticket", exception);
            }
        }
        chunkTickets.clear();
    }
}
