package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundExportServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void snapshotReadsFinishBeforeEncodingAndFilesAreWrittenOffThread() {
        AtomicReference<Runnable> snapshotTick = new AtomicReference<>();
        AtomicReference<Runnable> asyncWrite = new AtomicReference<>();
        BukkitTask task = proxy(BukkitTask.class, (ignored, method, arguments) -> null);
        BukkitScheduler scheduler =
                proxy(
                        BukkitScheduler.class,
                        (ignored, method, arguments) -> {
                            if (method.getName().equals("runTaskTimer")) {
                                snapshotTick.set((Runnable) arguments[1]);
                                return task;
                            }
                            if (method.getName().equals("runTaskAsynchronously")) {
                                asyncWrite.set((Runnable) arguments[1]);
                                return task;
                            }
                            if (method.getName().equals("runTask")) {
                                ((Runnable) arguments[1]).run();
                                return task;
                            }
                            if (method.getName().equals("runTaskLater")) {
                                return task;
                            }
                            return defaultValue(method.getReturnType());
                        });
        Server server =
                proxy(
                        Server.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getScheduler")
                                        ? scheduler
                                        : defaultValue(method.getReturnType()));
        Plugin plugin =
                proxy(
                        Plugin.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getServer")
                                        ? server
                                        : defaultValue(method.getReturnType()));
        World world =
                proxy(
                        World.class,
                        (ignored, method, arguments) -> {
                            if (method.getName().equals("getChunkAtAsync")) {
                                int chunkX = (int) arguments[0];
                                int chunkZ = (int) arguments[1];
                                Chunk chunk =
                                        proxy(
                                                Chunk.class,
                                                (unused, chunkMethod, chunkArguments) ->
                                                        switch (chunkMethod.getName()) {
                                                            case "getX" -> chunkX;
                                                            case "getZ" -> chunkZ;
                                                            default ->
                                                                    defaultValue(
                                                                            chunkMethod
                                                                                    .getReturnType());
                                                        });
                                return CompletableFuture.completedFuture(chunk);
                            }
                            if (method.getName().equals("addPluginChunkTicket")
                                    || method.getName().equals("removePluginChunkTicket")) {
                                return true;
                            }
                            if (!method.getName().equals("getBlockAt")) {
                                return defaultValue(method.getReturnType());
                            }
                            int x = (int) arguments[0];
                            int y = (int) arguments[1];
                            int z = (int) arguments[2];
                            Material material =
                                    x == 101 && y == 65 && z == 201
                                            ? Material.OAK_PLANKS
                                            : Material.AIR;
                            return proxy(
                                    Block.class,
                                    (unused, blockMethod, blockArguments) ->
                                            blockMethod.getName().equals("getType")
                                                    ? material
                                                    : defaultValue(
                                                            blockMethod.getReturnType()));
                        });
        Path roundsDirectory = temporaryDirectory.resolve("rounds");
        RoundExportService exporter =
                new RoundExportService(
                        plugin,
                        world,
                        4,
                        Clock.fixed(Instant.parse("2026-07-20T20:42:09Z"), ZoneOffset.UTC),
                        new RoundExportWriter(roundsDirectory),
                        Logger.getAnonymousLogger());
        RoundExportRequest request =
                new RoundExportRequest(
                        "Pirate ship",
                        "battle_world",
                        List.of(
                                new RoundExportRequest.Plot(
                                        "p1", "KidAva", 100, 64, 200, 2, 2, 2)));

        assertEquals("round-20260720-204209", exporter.export(request));
        assertTrue(exporter.isBusy());
        exporter.cancel();
        assertFalse(exporter.isBusy());
        snapshotTick.get().run();
        assertNull(asyncWrite.get());

        exporter.export(request);
        snapshotTick.get().run();
        assertTrue(exporter.isBusy());
        assertFalse(Files.exists(roundsDirectory.resolve("round-20260720-204209")));
        snapshotTick.get().run();

        assertNotNull(asyncWrite.get());
        assertTrue(exporter.isBusy());
        assertFalse(Files.exists(roundsDirectory.resolve("round-20260720-204209")));
        asyncWrite.get().run();

        Path roundDirectory = roundsDirectory.resolve("round-20260720-204209");
        assertTrue(Files.isRegularFile(roundDirectory.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(roundDirectory.resolve("p1.voxels.json")));
        assertFalse(exporter.isBusy());
        exporter.close();
    }

    @Test
    void partialChunkPreparationFailureCancelsLoadsThatAlreadyStarted() {
        CompletableFuture<Chunk> firstLoad = new CompletableFuture<>();
        AtomicInteger loadAttempts = new AtomicInteger();
        BukkitTask task = proxy(BukkitTask.class, (ignored, method, arguments) -> null);
        BukkitScheduler scheduler =
                proxy(
                        BukkitScheduler.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("runTaskTimer")
                                        ? task
                                        : defaultValue(method.getReturnType()));
        Server server =
                proxy(
                        Server.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getScheduler")
                                        ? scheduler
                                        : defaultValue(method.getReturnType()));
        Plugin plugin =
                proxy(
                        Plugin.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getServer")
                                        ? server
                                        : defaultValue(method.getReturnType()));
        World world =
                proxy(
                        World.class,
                        (ignored, method, arguments) -> {
                            if (!method.getName().equals("getChunkAtAsync")) {
                                return defaultValue(method.getReturnType());
                            }
                            if (loadAttempts.getAndIncrement() == 0) {
                                return firstLoad;
                            }
                            throw new IllegalStateException("second chunk load failed");
                        });
        RoundExportService exporter =
                new RoundExportService(
                        plugin,
                        world,
                        4,
                        Clock.systemUTC(),
                        new RoundExportWriter(temporaryDirectory.resolve("rounds")),
                        Logger.getAnonymousLogger());
        RoundExportRequest request =
                new RoundExportRequest(
                        "Pirate ship",
                        "battle_world",
                        List.of(
                                new RoundExportRequest.Plot(
                                        "p1", "KidAva", 0, 64, 0, 17, 1, 1)));

        exporter.export(request);

        assertTrue(firstLoad.isCancelled());
        assertFalse(exporter.isBusy());
        exporter.close();
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
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new AssertionError("Unhandled primitive " + type);
    }
}
