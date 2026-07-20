package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

final class BatchedBlockEditorTest {
    private static final SecretChestPosition SECRET_CHEST =
            new SecretChestPosition(2, 1, 0);

    @Test
    void preparesAndTicketsChunksBeforeRunningBudgetedBlockEdits() {
        TestRig rig = new TestRig(false, false, false);
        AtomicLong completedMutations = new AtomicLong();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        long scheduled =
                editor.enqueueArena(
                        List.of(new PlotBounds(0, 0, 0, 0)),
                        0,
                        1,
                        SECRET_CHEST,
                        completedMutations::set,
                        failure::set);

        assertEquals(18, scheduled);
        assertEquals(4, rig.chunkLoads.get());
        assertEquals(4, rig.ticketsAdded.get());
        assertTrue(editor.isBusy());
        assertNotNull(rig.tick.get());

        int ticks = 0;
        while (editor.isBusy()) {
            int before = rig.blockMutations.get();
            rig.tick.get().run();
            assertTrue(rig.blockMutations.get() - before <= 5);
            assertTrue(++ticks < 10);
        }

        assertEquals(18, completedMutations.get());
        assertEquals(18, rig.blockMutations.get());
        assertEquals(4, rig.ticketsRemoved.get());
        assertNull(failure.get());
        editor.close();
    }

    @Test
    void reportsChunkPreparationFailureWithoutStartingMutations() {
        TestRig rig = new TestRig(true, false, false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                ignored -> {},
                failure::set);

        assertFalse(editor.isBusy());
        assertNotNull(failure.get());
        assertEquals(0, rig.blockMutations.get());
        assertEquals(0, rig.ticketsAdded.get());
        editor.close();
    }

    @Test
    void reportsSynchronousSchedulerHandoffFailureBeforeReturning() {
        TestRig rig = new TestRig(false, true, false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                ignored -> {},
                failure::set);

        assertFalse(editor.isBusy());
        assertNotNull(failure.get());
        assertEquals(0, rig.blockMutations.get());
        editor.close();
    }

    @Test
    void timesOutHungChunkPreparationAndAllowsRetry() {
        TestRig rig = new TestRig(false, false, false, true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                ignored -> {},
                failure::set);
        assertTrue(editor.isBusy());
        assertNotNull(rig.preparationTimeout.get());

        rig.preparationTimeout.get().run();

        assertFalse(editor.isBusy());
        assertTrue(failure.get() instanceof TimeoutException);
        assertEquals(0, editor.pendingBlocks());
        assertEquals(0, rig.blockMutations.get());
        editor.close();
    }

    @Test
    void completedChunksWinRaceWithPreparationTimeout() {
        TestRig rig = new TestRig(false, false, false, false, true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong completedMutations = new AtomicLong();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                completedMutations::set,
                failure::set);
        assertNotNull(rig.preparationHandoff.get());

        rig.preparationTimeout.get().run();
        assertTrue(editor.isBusy());
        assertNull(failure.get());

        rig.preparationHandoff.get().run();
        while (editor.isBusy()) {
            rig.tick.get().run();
        }

        assertEquals(18, completedMutations.get());
        assertNull(failure.get());
        editor.close();
    }

    @Test
    void recoversFromMutationFailureAndReleasesTickets() {
        TestRig rig = new TestRig(false, false, true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong completedMutations = new AtomicLong();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 5, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                completedMutations::set,
                failure::set);
        rig.tick.get().run();

        assertFalse(editor.isBusy());
        assertEquals(0, editor.pendingBlocks());
        assertNotNull(failure.get());
        assertEquals(0, completedMutations.get());
        assertEquals(4, rig.ticketsRemoved.get());
        editor.close();
    }

    @Test
    void cancelAbortsQueuedWorkAndAllowsAReplacement() {
        TestRig rig = new TestRig(false, false, false);
        AtomicLong completedMutations = new AtomicLong();
        BatchedBlockEditor editor =
                new BatchedBlockEditor(
                        rig.plugin, rig.world, 1, Logger.getAnonymousLogger());

        editor.enqueueArena(
                List.of(new PlotBounds(0, 0, 0, 0)),
                0,
                1,
                SECRET_CHEST,
                completedMutations::set,
                ignored -> {});
        rig.tick.get().run();
        editor.cancel();

        assertFalse(editor.isBusy());
        assertEquals(0, editor.pendingBlocks());
        assertEquals(0, completedMutations.get());
        assertEquals(4, rig.ticketsRemoved.get());

        long revealMutations =
                editor.enqueueWallRemoval(
                        List.of(new PlotBounds(0, 0, 0, 0)),
                        0,
                        1,
                        completedMutations::set,
                        ignored -> {});
        while (editor.isBusy()) {
            rig.tick.get().run();
        }

        assertEquals(8, revealMutations);
        assertEquals(revealMutations, completedMutations.get());
        editor.close();
    }

    private static final class TestRig {
        private final AtomicInteger blockMutations = new AtomicInteger();
        private final AtomicInteger chunkLoads = new AtomicInteger();
        private final AtomicInteger ticketsAdded = new AtomicInteger();
        private final AtomicInteger ticketsRemoved = new AtomicInteger();
        private final AtomicReference<Runnable> tick = new AtomicReference<>();
        private final AtomicReference<Runnable> preparationTimeout = new AtomicReference<>();
        private final AtomicReference<Runnable> preparationHandoff = new AtomicReference<>();
        private final Plugin plugin;
        private final World world;

        private TestRig(
                boolean failChunkLoad,
                boolean failPreparationHandoff,
                boolean failFirstBlockMutation) {
            this(
                    failChunkLoad,
                    failPreparationHandoff,
                    failFirstBlockMutation,
                    false,
                    false);
        }

        private TestRig(
                boolean failChunkLoad,
                boolean failPreparationHandoff,
                boolean failFirstBlockMutation,
                boolean hangChunkLoad) {
            this(
                    failChunkLoad,
                    failPreparationHandoff,
                    failFirstBlockMutation,
                    hangChunkLoad,
                    false);
        }

        private TestRig(
                boolean failChunkLoad,
                boolean failPreparationHandoff,
                boolean failFirstBlockMutation,
                boolean hangChunkLoad,
                boolean deferPreparationHandoff) {
            BukkitTask task = proxy(BukkitTask.class, (ignored, method, arguments) -> null);
            Block block =
                    proxy(
                            Block.class,
                            (ignored, method, arguments) -> {
                                if (method.getName().equals("setType")) {
                                    int mutation = blockMutations.incrementAndGet();
                                    if (failFirstBlockMutation && mutation == 1) {
                                        throw new IllegalStateException(
                                                "test block mutation failure");
                                    }
                                }
                                return defaultValue(method.getReturnType());
                            });
            world =
                    proxy(
                            World.class,
                            (ignored, method, arguments) -> {
                                return switch (method.getName()) {
                                    case "getChunkAtAsync" -> {
                                        int loadNumber = chunkLoads.incrementAndGet();
                                        int chunkX = (int) arguments[0];
                                        int chunkZ = (int) arguments[1];
                                        if (hangChunkLoad) {
                                            yield new CompletableFuture<>();
                                        }
                                        if (failChunkLoad && loadNumber == 1) {
                                            yield CompletableFuture.failedFuture(
                                                    new IllegalStateException(
                                                            "test chunk load failure"));
                                        }
                                        yield CompletableFuture.completedFuture(
                                                chunk(chunkX, chunkZ));
                                    }
                                    case "addPluginChunkTicket" -> {
                                        ticketsAdded.incrementAndGet();
                                        yield true;
                                    }
                                    case "removePluginChunkTicket" -> {
                                        ticketsRemoved.incrementAndGet();
                                        yield true;
                                    }
                                    case "getBlockAt" -> block;
                                    case "getSpawnLocation" -> new Location(null, 0, 0, 0);
                                    case "getName" -> ArenaWorldService.WORLD_NAME;
                                    default -> defaultValue(method.getReturnType());
                                };
                            });
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (ignored, method, arguments) -> {
                                return switch (method.getName()) {
                                    case "runTaskTimer" -> {
                                        tick.set((Runnable) arguments[1]);
                                        yield task;
                                    }
                                    case "runTask" -> {
                                        if (failPreparationHandoff) {
                                            throw new IllegalStateException(
                                                    "test scheduler handoff failure");
                                        }
                                        if (deferPreparationHandoff) {
                                            preparationHandoff.set((Runnable) arguments[1]);
                                        } else {
                                            ((Runnable) arguments[1]).run();
                                        }
                                        yield task;
                                    }
                                    case "runTaskLater" -> {
                                        preparationTimeout.set((Runnable) arguments[1]);
                                        yield task;
                                    }
                                    default -> defaultValue(method.getReturnType());
                                };
                            });
            Server server =
                    proxy(
                            Server.class,
                            (ignored, method, arguments) ->
                                    method.getName().equals("getScheduler")
                                            ? scheduler
                                            : defaultValue(method.getReturnType()));
            plugin =
                    proxy(
                            Plugin.class,
                            (ignored, method, arguments) ->
                                    method.getName().equals("getServer")
                                            ? server
                                            : defaultValue(method.getReturnType()));
        }

        private static Chunk chunk(int x, int z) {
            return proxy(
                    Chunk.class,
                    (ignored, method, arguments) ->
                            switch (method.getName()) {
                                case "getX" -> x;
                                case "getZ" -> z;
                                default -> defaultValue(method.getReturnType());
                            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
