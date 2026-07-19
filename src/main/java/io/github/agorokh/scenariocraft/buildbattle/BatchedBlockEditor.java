package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns the single per-tick queue used to clear plots and build their walls. */
public final class BatchedBlockEditor implements AutoCloseable {
    private final World world;
    private final BatchedWorkQueue queue;
    private final Logger logger;
    private final BukkitTask task;
    private Runnable completion;

    public BatchedBlockEditor(Plugin plugin, World world, int blocksPerTick, Logger logger) {
        this.world = Objects.requireNonNull(world, "world");
        this.queue = new BatchedWorkQueue(blocksPerTick);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::runTick, 1L, 1L);
    }

    public long enqueueArena(
            List<PlotBounds> plots, int floorY, int wallHeight, LongConsumer onComplete) {
        if (queue.hasWork()) {
            throw new IllegalStateException("an arena build is already queued");
        }
        ArenaFillPlan plan = ArenaFillPlan.forPlots(plots, floorY, wallHeight);
        for (BlockFill fill : plan.fills()) {
            queue.add(new BlockFillOperation(world, fill.bounds(), fill.material()));
        }
        long scheduledBlocks = plan.totalBlockMutations();
        LongConsumer completionConsumer = Objects.requireNonNull(onComplete, "onComplete");
        completion = () -> completionConsumer.accept(scheduledBlocks);
        return scheduledBlocks;
    }

    public boolean isBusy() {
        return queue.hasWork();
    }

    public long pendingBlocks() {
        return queue.pendingSteps();
    }

    @Override
    public void close() {
        task.cancel();
        completion = null;
    }

    private void runTick() {
        boolean hadWork = queue.hasWork();
        queue.runTick();
        if (hadWork && !queue.hasWork() && completion != null) {
            Runnable finished = completion;
            completion = null;
            try {
                finished.run();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Arena completion callback failed", exception);
            }
        }
    }
}
