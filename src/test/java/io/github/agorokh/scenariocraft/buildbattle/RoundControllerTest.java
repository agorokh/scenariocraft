package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
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
        assertEquals(GameMode.ADVENTURE, rig.gameMode.get());
        assertTrue(rig.inventoryClears.get() > 0);
        assertTrue(rig.enderChestClears.get() > 0);
        assertTrue(rig.closedInventories.get() > 0);
        assertTrue(rig.cursorClears.get() > 0);
        assertTrue(rig.saveDataCalls.get() > 0);
        assertFalse(rig.persistentData.isEmpty());

        rig.runBlockTick();
        assertEquals(RoundPhase.GATHERING, rig.controller.phase());

        rig.runTimerTick();
        assertEquals(RoundPhase.NOTE_PICK, rig.controller.phase());
        assertTrue(rig.titles.get() > 0);

        rig.runTimerTick();
        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        assertTrue(rig.bossbarPlayers.get() > 0);
        int inventoryClearsBeforeReveal = rig.inventoryClears.get();

        rig.runTimerTick();
        assertEquals(RoundPhase.REVEAL, rig.controller.phase());
        assertEquals(GameMode.ADVENTURE, rig.gameMode.get());
        assertTrue(rig.inventoryClears.get() > inventoryClearsBeforeReveal);

        rig.runBlockTick();
        rig.runTimerTick();

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        assertTrue(rig.persistentData.isEmpty());
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

    @Test
    void reconnectingContestantReceivesTheCurrentPhaseState() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        int titlesBeforeReconnect = rig.titles.get();
        rig.gameMode.set(GameMode.SURVIVAL);

        rig.controller.onPlayerJoin(
                new org.bukkit.event.player.PlayerJoinEvent(
                        rig.player, net.kyori.adventure.text.Component.empty()));

        assertEquals(GameMode.ADVENTURE, rig.gameMode.get());
        assertTrue(rig.titles.get() > titlesBeforeReconnect);

        rig.runTimerTick();
        rig.gameMode.set(GameMode.SURVIVAL);
        rig.lastTeleport.set(null);
        rig.controller.onPlayerJoin(
                new org.bukkit.event.player.PlayerJoinEvent(
                        rig.player, net.kyori.adventure.text.Component.empty()));

        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        assertNotNull(rig.lastTeleport.get());
        assertTrue(
                Math.abs(rig.lastTeleport.get().getX()) > 1.0
                        || Math.abs(rig.lastTeleport.get().getZ()) > 1.0);
        assertTrue(rig.bossbarPlayers.get() > 0);
        rig.close();
    }

    @Test
    void disconnectingContestantIsRestoredBeforeRoundStateIsCleared() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.BUILDING);
        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        rig.inventoryContents.set(new ItemStack[1]);
        rig.enderChestContents.set(new ItemStack[1]);

        rig.controller.onPlayerQuit(
                new PlayerQuitEvent(
                        rig.player,
                        net.kyori.adventure.text.Component.empty(),
                        PlayerQuitEvent.QuitReason.DISCONNECTED));
        rig.playerOnline.set(false);

        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        assertEquals(2, rig.inventoryContents.get().length);
        assertEquals(3, rig.enderChestContents.get().length);
        assertEquals(0.5, rig.lastTeleport.get().getX());
        assertEquals(0.5, rig.lastTeleport.get().getZ());

        rig.controller.stop(rig.player);
        rig.playerOnline.set(true);
        rig.controller.onPlayerJoin(
                new PlayerJoinEvent(
                        rig.player, net.kyori.adventure.text.Component.empty()));

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        rig.close();
    }

    @Test
    void revealRestoresExemptSpectatorToTheirOriginalState() {
        TestRig rig = new TestRig();
        Location originalLocation = rig.spectatorLocation.get().clone();
        GameMode originalGameMode = rig.spectatorGameMode.get();

        rig.advanceTo(RoundPhase.REVEAL);
        assertEquals(GameMode.ADVENTURE, rig.spectatorGameMode.get());
        assertEquals(0.5, rig.spectatorLocation.get().getX());
        assertEquals(0.5, rig.spectatorLocation.get().getZ());

        rig.runBlockTick();
        rig.runTimerTick();

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertEquals(originalGameMode, rig.spectatorGameMode.get());
        assertEquals(originalLocation.getX(), rig.spectatorLocation.get().getX());
        assertEquals(originalLocation.getY(), rig.spectatorLocation.get().getY());
        assertEquals(originalLocation.getZ(), rig.spectatorLocation.get().getZ());
        rig.close();
    }

    @Test
    void revealLingerDoesNotBroadcastPeriodicCountdownSpam() {
        TestRig rig = new TestRig(new PhaseTimings(1, 1, 1, 20));
        rig.advanceTo(RoundPhase.REVEAL);
        rig.runBlockTick();

        long revealCountdownsBefore =
                rig.messages.stream().filter(message -> message.startsWith("reveal:")).count();
        for (int tick = 0; tick < 10; tick++) {
            rig.runTimerTick();
        }

        assertEquals(
                revealCountdownsBefore,
                rig.messages.stream().filter(message -> message.startsWith("reveal:")).count());
        rig.close();
    }

    @Test
    void arenaFailureNotifiesConsoleStarterDirectly() {
        TestRig rig = new TestRig();
        rig.failChunkLoads.set(true);

        rig.controller.start(rig.consoleStarter);
        rig.runBlockTick();

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertTrue(
                rig.starterMessages.stream()
                        .anyMatch(message -> message.startsWith("The arena could not get ready")));
        rig.close();
    }

    @Test
    void synchronousArenaFailureAbortsAndRestoresContestants() {
        TestRig rig =
                new TestRig(
                        new PhaseTimings(1, 1, 1, 1),
                        Integer.MAX_VALUE);

        rig.controller.start(rig.consoleStarter);

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        assertTrue(rig.persistentData.isEmpty());
        assertTrue(
                rig.starterMessages.stream()
                        .anyMatch(message -> message.startsWith("The arena could not get ready")));
        rig.close();
    }

    @Test
    void pendingInventorySnapshotRestoresWhenControllerReinitializes() {
        TestRig rig = new TestRig();
        rig.controller.start(rig.player);
        rig.inventoryContents.set(new ItemStack[1]);
        rig.enderChestContents.set(new ItemStack[1]);
        assertFalse(rig.persistentData.isEmpty());

        RoundController replacement =
                new RoundController(
                        rig.plugin,
                        rig.settings,
                        rig.arena,
                        rig.editor,
                        Logger.getAnonymousLogger());

        assertTrue(rig.persistentData.isEmpty());
        assertEquals(2, rig.inventoryContents.get().length);
        assertEquals(3, rig.enderChestContents.get().length);
        assertEquals(GameMode.SURVIVAL, rig.gameMode.get());
        replacement.close();
        rig.close();
    }

    @Test
    void contestantDropsAreBlockedOnlyDuringAnActiveRound() {
        TestRig rig = new TestRig();
        Item droppedItem =
                proxy(
                        Item.class,
                        (ignored, method, arguments) ->
                                defaultValue(method.getReturnType()));

        rig.controller.start(rig.player);
        PlayerDropItemEvent activeDrop =
                new PlayerDropItemEvent(rig.player, droppedItem);
        rig.controller.onPlayerDropItem(activeDrop);
        assertTrue(activeDrop.isCancelled());
        EntityPickupItemEvent activePickup =
                new EntityPickupItemEvent(rig.player, droppedItem, 0);
        rig.controller.onEntityPickupItem(activePickup);
        assertTrue(activePickup.isCancelled());

        rig.controller.stop(rig.player);
        PlayerDropItemEvent idleDrop =
                new PlayerDropItemEvent(rig.player, droppedItem);
        rig.controller.onPlayerDropItem(idleDrop);
        assertFalse(idleDrop.isCancelled());
        EntityPickupItemEvent idlePickup =
                new EntityPickupItemEvent(rig.player, droppedItem, 0);
        rig.controller.onEntityPickupItem(idlePickup);
        assertFalse(idlePickup.isCancelled());
        rig.close();
    }

    @Test
    void reconnectSaveFailureStillVacatesActiveContestantInventory() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.BUILDING);
        rig.controller.onPlayerQuit(
                new PlayerQuitEvent(
                        rig.player,
                        net.kyori.adventure.text.Component.empty(),
                        PlayerQuitEvent.QuitReason.DISCONNECTED));
        rig.playerOnline.set(false);
        rig.playerOnline.set(true);
        rig.failSaveData.set(true);
        int clearsBefore = rig.inventoryClears.get();

        rig.controller.onPlayerJoin(
                new PlayerJoinEvent(
                        rig.player, net.kyori.adventure.text.Component.empty()));

        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        assertTrue(rig.inventoryClears.get() > clearsBefore);
        rig.failSaveData.set(false);
        rig.controller.stop(rig.player);
        rig.close();
    }

    private static final class TestRig {
        private final AtomicReference<Runnable> blockTick = new AtomicReference<>();
        private final AtomicReference<Runnable> timerTick = new AtomicReference<>();
        private final AtomicBoolean playerOnline = new AtomicBoolean(true);
        private final AtomicBoolean failChunkLoads = new AtomicBoolean();
        private final AtomicBoolean failSaveData = new AtomicBoolean();
        private final AtomicReference<GameMode> gameMode =
                new AtomicReference<>(GameMode.SURVIVAL);
        private final AtomicReference<GameMode> spectatorGameMode =
                new AtomicReference<>(GameMode.SURVIVAL);
        private final AtomicInteger blockMutations = new AtomicInteger();
        private final AtomicInteger bossbarPlayers = new AtomicInteger();
        private final AtomicInteger titles = new AtomicInteger();
        private final AtomicInteger inventoryClears = new AtomicInteger();
        private final AtomicInteger enderChestClears = new AtomicInteger();
        private final AtomicInteger closedInventories = new AtomicInteger();
        private final AtomicInteger cursorClears = new AtomicInteger();
        private final AtomicInteger saveDataCalls = new AtomicInteger();
        private final AtomicReference<Location> lastTeleport = new AtomicReference<>();
        private final AtomicReference<Location> spectatorLocation = new AtomicReference<>();
        private final AtomicReference<ItemStack> cursorItem = new AtomicReference<>();
        private final AtomicReference<ItemStack[]> inventoryContents =
                new AtomicReference<>(new ItemStack[2]);
        private final AtomicReference<ItemStack[]> enderChestContents =
                new AtomicReference<>(new ItemStack[3]);
        private final List<String> messages = new ArrayList<>();
        private final List<String> starterMessages = new ArrayList<>();
        private final Map<NamespacedKey, Object> persistentData = new HashMap<>();
        private final Map<NamespacedKey, Object> spectatorPersistentData = new HashMap<>();
        private final World world;
        private final PlayerInventory playerInventory;
        private final Inventory enderChest;
        private final PersistentDataContainer persistentDataContainer;
        private final PersistentDataContainer spectatorPersistentDataContainer;
        private final Player player;
        private final Player spectator;
        private final CommandSender consoleStarter;
        private final Plugin plugin;
        private final BattleSettings settings;
        private final ArenaWorld arena;
        private final BatchedBlockEditor editor;
        private final RoundController controller;

        private TestRig() {
            this(new PhaseTimings(1, 1, 1, 1), 0);
        }

        private TestRig(PhaseTimings timings) {
            this(timings, 0);
        }

        private TestRig(PhaseTimings timings, int floorY) {
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
                                        case "getChunkAtAsync" -> {
                                            if (failChunkLoads.get()) {
                                                yield CompletableFuture.failedFuture(
                                                        new IllegalStateException(
                                                                "test chunk load failure"));
                                            }
                                            yield CompletableFuture.completedFuture(
                                                    chunk((int) arguments[0], (int) arguments[1]));
                                        }
                                        case "addPluginChunkTicket", "removePluginChunkTicket" -> true;
                                        case "getBlockAt" -> block;
                                        default -> defaultValue(method.getReturnType());
                                    });
            playerInventory =
                    trackedInventory(
                            PlayerInventory.class, inventoryContents, inventoryClears);
            enderChest =
                    trackedInventory(Inventory.class, enderChestContents, enderChestClears);
            persistentDataContainer = trackedPersistentData(persistentData);
            spectatorPersistentDataContainer =
                    trackedPersistentData(spectatorPersistentData);
            UUID playerId = UUID.fromString("9a49fbc6-1d0b-4b12-a37b-cbb1b0f6d5cc");
            player =
                    proxy(
                            Player.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> playerId;
                                        case "getName" -> "BuilderKid";
                                        case "getGameMode" -> gameMode.get();
                                        case "getInventory" -> playerInventory;
                                        case "getEnderChest" -> enderChest;
                                        case "getPersistentDataContainer" ->
                                            persistentDataContainer;
                                        case "getItemOnCursor" -> cursorItem.get();
                                        case "setGameMode" -> {
                                            gameMode.set((GameMode) arguments[0]);
                                            yield null;
                                        }
                                        case "isOnline" -> playerOnline.get();
                                        case "isOp" -> true;
                                        case "closeInventory" -> {
                                            closedInventories.incrementAndGet();
                                            yield null;
                                        }
                                        case "setItemOnCursor" -> {
                                            cursorItem.set((ItemStack) arguments[0]);
                                            cursorClears.incrementAndGet();
                                            yield null;
                                        }
                                        case "saveData" -> {
                                            if (failSaveData.get()) {
                                                throw new IllegalStateException(
                                                        "test save failure");
                                            }
                                            saveDataCalls.incrementAndGet();
                                            yield null;
                                        }
                                        case "teleport" -> {
                                            lastTeleport.set((Location) arguments[0]);
                                            yield true;
                                        }
                                        case "sendMessage" -> {
                                            if (arguments[0] instanceof String message) {
                                                messages.add(message);
                                            }
                                            yield null;
                                        }
                                        case "sendTitle" -> {
                                            titles.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            UUID spectatorId = UUID.fromString("726ee348-f967-4e3c-96fd-c3c012bb59a6");
            spectatorLocation.set(new Location(world, 40.5, 8.0, -12.5));
            spectator =
                    proxy(
                            Player.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> spectatorId;
                                        case "getName" -> "Parent";
                                        case "getGameMode" -> spectatorGameMode.get();
                                        case "getPersistentDataContainer" ->
                                            spectatorPersistentDataContainer;
                                        case "setGameMode" -> {
                                            spectatorGameMode.set((GameMode) arguments[0]);
                                            yield null;
                                        }
                                        case "getLocation" -> spectatorLocation.get().clone();
                                        case "isOnline", "isOp" -> true;
                                        case "teleport" -> {
                                            spectatorLocation.set(((Location) arguments[0]).clone());
                                            yield true;
                                        }
                                        case "sendMessage" -> {
                                            if (arguments[0] instanceof String message) {
                                                messages.add(message);
                                            }
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            consoleStarter =
                    proxy(
                            CommandSender.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getName" -> "CONSOLE";
                                        case "isOp" -> true;
                                        case "sendMessage" -> {
                                            if (arguments[0] instanceof String message) {
                                                starterMessages.add(message);
                                            }
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
                                        case "getOnlinePlayers" ->
                                                (Collection<Player>) List.of(player, spectator);
                                        case "getPlayer" -> {
                                            if (playerId.equals(arguments[0])) {
                                                yield player;
                                            }
                                            yield spectatorId.equals(arguments[0])
                                                    ? spectator
                                                    : null;
                                        }
                                        case "createBossBar" -> bossBar;
                                        default -> defaultValue(method.getReturnType());
                                    });
            plugin =
                    proxy(
                            Plugin.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getServer" -> server;
                                        case "getName" -> "ScenarioCraft";
                                        case "namespace" -> "scenariocraft";
                                        default -> defaultValue(method.getReturnType());
                                    });
            settings =
                    new BattleSettings(
                            new ArenaSettings(1, 1, 3, 2, 1_000),
                            timings,
                            List.of("A dragon treehouse"),
                            List.of("Parent"),
                            true);
            arena = new ArenaWorld(world, floorY);
            editor =
                    new BatchedBlockEditor(plugin, world, 1_000, Logger.getAnonymousLogger());
            controller =
                    new RoundController(
                            plugin,
                            settings,
                            arena,
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

        private static <T extends Inventory> T trackedInventory(
                Class<T> type,
                AtomicReference<ItemStack[]> contents,
                AtomicInteger clearCount) {
            return proxy(
                    type,
                    (ignored, method, arguments) ->
                            switch (method.getName()) {
                                case "getContents" -> contents.get();
                                case "setContents" -> {
                                    contents.set(((ItemStack[]) arguments[0]).clone());
                                    yield null;
                                }
                                case "clear" -> {
                                    clearCount.incrementAndGet();
                                    contents.set(new ItemStack[contents.get().length]);
                                    yield null;
                                }
                                default -> defaultValue(method.getReturnType());
                            });
        }

        private static PersistentDataContainer trackedPersistentData(
                Map<NamespacedKey, Object> data) {
            return proxy(
                    PersistentDataContainer.class,
                    (ignored, method, arguments) ->
                            switch (method.getName()) {
                                case "set" -> {
                                    data.put((NamespacedKey) arguments[0], arguments[2]);
                                    yield null;
                                }
                                case "get" -> data.get(arguments[0]);
                                case "has" -> data.containsKey(arguments[0]);
                                case "remove" -> {
                                    data.remove(arguments[0]);
                                    yield null;
                                }
                                case "isEmpty" -> data.isEmpty();
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
