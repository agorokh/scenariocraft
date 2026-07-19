package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class RoundControllerTest {
    @Test
    void onePlayerAndDebugFillRunTheCompleteTimedCycle() {
        TestRig rig = new TestRig();

        rig.controller.start(rig.player);
        assertEquals(RoundPhase.PREPARING, rig.controller.phase());

        rig.runBlockTick();
        assertEquals(RoundPhase.GATHERING, rig.controller.phase());

        rig.runTimerTick();
        assertEquals(RoundPhase.NOTE_PICK, rig.controller.phase());
        assertTrue(rig.titles.get() > 0);

        rig.runTimerTick();
        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        assertTrue(rig.bossbarPlayers.get() > 0);

        rig.runTimerTick();
        assertEquals(RoundPhase.REVEAL, rig.controller.phase());
        assertEquals(GameMode.ADVENTURE, rig.gameMode.get());

        rig.runBlockTick();
        rig.runTimerTick();

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        assertFalse(rig.editor.isBusy());
        assertTrue(rig.blockMutations.get() > 0);
        rig.close();
    }

    @Test
    void adminStopAbortsCleanlyFromEveryActivePhase() {
        for (RoundPhase phase :
                List.of(
                        RoundPhase.PREPARING,
                        RoundPhase.GATHERING,
                        RoundPhase.NOTE_PICK,
                        RoundPhase.BUILDING,
                        RoundPhase.REVEAL)) {
            TestRig rig = new TestRig();
            rig.advanceTo(phase);

            rig.controller.stop(rig.player);

            assertEquals(RoundPhase.IDLE, rig.controller.phase(), phase.toString());
            assertFalse(rig.editor.isBusy(), phase.toString());
            assertEquals(GameMode.SURVIVAL, rig.gameMode.get(), phase.toString());
            rig.close();
        }
    }

    private static final class TestRig {
        private final AtomicReference<Runnable> blockTick = new AtomicReference<>();
        private final AtomicReference<Runnable> timerTick = new AtomicReference<>();
        private final AtomicReference<GameMode> gameMode =
                new AtomicReference<>(GameMode.SURVIVAL);
        private final AtomicInteger blockMutations = new AtomicInteger();
        private final AtomicInteger bossbarPlayers = new AtomicInteger();
        private final AtomicInteger titles = new AtomicInteger();
        private final World world;
        private final Player player;
        private final Plugin plugin;
        private final BatchedBlockEditor editor;
        private final RoundController controller;

        private TestRig() {
            BukkitTask task =
                    proxy(
                            BukkitTask.class,
                            (ignored, method, arguments) -> defaultValue(method.getReturnType()));
            BossBar bossBar =
                    proxy(
                            BossBar.class,
                            (ignored, method, arguments) -> {
                                switch (method.getName()) {
                                    case "addPlayer" -> bossbarPlayers.incrementAndGet();
                                    case "removePlayer" ->
                                            bossbarPlayers.updateAndGet(value -> Math.max(0, value - 1));
                                    case "removeAll" -> bossbarPlayers.set(0);
                                    default -> {
                                        // Other bossbar effects are presentation-only in this test.
                                    }
                                }
                                return defaultValue(method.getReturnType());
                            });
            Block block =
                    proxy(
                            Block.class,
                            (ignored, method, arguments) -> {
                                if (method.getName().equals("setType")) {
                                    blockMutations.incrementAndGet();
                                }
                                return defaultValue(method.getReturnType());
                            });
            world =
                    proxy(
                            World.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getSpawnLocation" -> new Location(worldRef(), 0, 1, 0);
                                        case "getName" -> ArenaWorldService.WORLD_NAME;
                                        case "getChunkAtAsync" ->
                                            CompletableFuture.completedFuture(
                                                    chunk((int) arguments[0], (int) arguments[1]));
                                        case "addPluginChunkTicket", "removePluginChunkTicket" -> true;
                                        case "getBlockAt" -> block;
                                        default -> defaultValue(method.getReturnType());
                                    });
            UUID playerId = UUID.fromString("9a49fbc6-1d0b-4b12-a37b-cbb1b0f6d5cc");
            player =
                    proxy(
                            Player.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> playerId;
                                        case "getName" -> "BuilderKid";
                                        case "getGameMode" -> gameMode.get();
                                        case "setGameMode" -> {
                                            gameMode.set((GameMode) arguments[0]);
                                            yield null;
                                        }
                                        case "isOnline", "isOp", "teleport" -> true;
                                        case "sendTitle" -> {
                                            titles.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            PluginManager pluginManager =
                    proxy(
                            PluginManager.class,
                            (ignored, method, arguments) -> defaultValue(method.getReturnType()));
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "runTaskTimer" -> {
                                            Runnable runnable = (Runnable) arguments[1];
                                            long period = (long) arguments[3];
                                            if (period == 1L) {
                                                blockTick.set(runnable);
                                            } else if (period == 20L) {
                                                timerTick.set(runnable);
                                            }
                                            yield task;
                                        }
                                        case "runTask" -> {
                                            ((Runnable) arguments[1]).run();
                                            yield task;
                                        }
                                        case "runTaskLater" -> task;
                                        default -> defaultValue(method.getReturnType());
                                    });
            Server server =
                    proxy(
                            Server.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getPluginManager" -> pluginManager;
                                        case "getOnlinePlayers" -> (Collection<Player>) List.of(player);
                                        case "getPlayer" -> playerId.equals(arguments[0]) ? player : null;
                                        case "createBossBar" -> bossBar;
                                        default -> defaultValue(method.getReturnType());
                                    });
            plugin =
                    proxy(
                            Plugin.class,
                            (ignored, method, arguments) ->
                                    method.getName().equals("getServer")
                                            ? server
                                            : defaultValue(method.getReturnType()));
            BattleSettings settings =
                    new BattleSettings(
                            new ArenaSettings(1, 1, 3, 2, 1_000),
                            new PhaseTimings(1, 1, 1, 1),
                            List.of("A dragon treehouse"),
                            List.of(),
                            true);
            editor =
                    new BatchedBlockEditor(plugin, world, 1_000, Logger.getAnonymousLogger());
            controller =
                    new RoundController(
                            plugin,
                            settings,
                            new ArenaWorld(world, 0),
                            editor,
                            Logger.getAnonymousLogger());
            assertNotNull(blockTick.get());
            assertNotNull(timerTick.get());
        }

        private World worldRef() {
            return world;
        }

        private void advanceTo(RoundPhase desired) {
            controller.start(player);
            if (desired == RoundPhase.PREPARING) {
                return;
            }
            runBlockTick();
            if (desired == RoundPhase.GATHERING) {
                return;
            }
            runTimerTick();
            if (desired == RoundPhase.NOTE_PICK) {
                return;
            }
            runTimerTick();
            if (desired == RoundPhase.BUILDING) {
                return;
            }
            runTimerTick();
            assertEquals(RoundPhase.REVEAL, desired);
        }

        private void runBlockTick() {
            blockTick.get().run();
        }

        private void runTimerTick() {
            timerTick.get().run();
        }

        private void close() {
            controller.close();
            editor.close();
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
