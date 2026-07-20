package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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
    void nonPickerCannotOpenTheSecretChestAndReceivesFriendlyFeedback() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        PlayerInteractEvent interaction = rig.chestInteraction(rig.spectator);

        rig.controller.onPlayerInteract(interaction);

        assertTrue(interaction.isCancelled());
        assertEquals(RoundPhase.NOTE_PICK, rig.controller.phase());
        assertTrue(
                rig.spectatorMessages.stream()
                        .anyMatch(message -> message.contains("belongs to BuilderKid")));
        rig.close();
    }

    @Test
    void secretChestCannotBeBrokenByAWaitingPlayer() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        BlockBreakEvent breakEvent =
                new BlockBreakEvent(rig.secretChestBlock, rig.spectator);

        rig.controller.onSecretChestBreak(breakEvent);

        assertTrue(breakEvent.isCancelled());
        assertTrue(
                rig.spectatorMessages.stream()
                        .anyMatch(message -> message.contains("stays right here")));
        rig.close();
    }

    @Test
    void pickerOpeningChestRevealsTaskToEveryPlayerAndStartsBuilding() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        PlayerInteractEvent interaction = rig.chestInteraction(rig.player);
        assertEquals("A dragon treehouse", rig.placedTask.get());
        assertTrue(
                rig.playerMessages.contains(
                        "BuilderKid is the secret-note picker! The chest is waiting at the hub."));
        assertTrue(
                rig.spectatorMessages.contains(
                        "BuilderKid is the secret-note picker! The chest is waiting at the hub."));
        assertTrue(
                rig.playerTitles.contains(
                        "BuilderKid has the secret note! | Open the chest at the hub!"));
        assertTrue(
                rig.spectatorTitles.contains(
                        "BuilderKid has the secret note! | They'll reveal the build idea!"));

        rig.controller.onPlayerInteract(interaction);

        assertFalse(interaction.isCancelled());
        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertTrue(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        assertTrue(
                rig.spectatorMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        assertTrue(rig.playerTitles.contains("Build idea! | A dragon treehouse"));
        assertTrue(rig.spectatorTitles.contains("Build idea! | A dragon treehouse"));
        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        rig.close();
    }

    @Test
    void offHandChestInteractionIsCancelledWithoutRevealing() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        PlayerInteractEvent interaction =
                rig.chestInteraction(rig.player, EquipmentSlot.OFF_HAND);

        rig.controller.onPlayerInteract(interaction);

        assertTrue(interaction.isCancelled());
        assertEquals(RoundPhase.NOTE_PICK, rig.controller.phase());
        assertFalse(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        rig.close();
    }

    @Test
    void pickerRevealStartsAFreshBuildCountdown() {
        TestRig rig = new TestRig(new PhaseTimings(1, 1, 3, 1));
        rig.advanceTo(RoundPhase.NOTE_PICK);

        rig.controller.onPlayerInteract(rig.chestInteraction(rig.player));
        rig.runTimerTick();

        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        rig.close();
    }

    @Test
    void cosmeticBookFailureStillAllowsPickerAndAfkReveal() {
        TestRig rig = new TestRig();
        rig.failTaskBookPlacement.set(true);

        rig.advanceTo(RoundPhase.NOTE_PICK);

        assertEquals(RoundPhase.NOTE_PICK, rig.controller.phase());
        assertTrue(
                rig.playerMessages.contains(
                        "BuilderKid is the secret-note picker! The chest is waiting at the hub."));

        rig.runTimerTick();

        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertTrue(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        rig.close();
    }

    @Test
    void afkTimeoutRevealsTaskAndProceedsToBuildingWithoutInteraction() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        assertFalse(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));

        rig.runTimerTick();

        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertTrue(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        assertTrue(
                rig.spectatorMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        assertTrue(
                rig.messages.contains(
                        "Time is up — the secret note opened itself for everyone!"));
        assertEquals(GameMode.CREATIVE, rig.gameMode.get());
        rig.close();
    }

    @Test
    void schedulerRejectionAfterPickerRevealStillProceedsToBuilding() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.NOTE_PICK);
        rig.failRunTask.set(true);

        rig.controller.onPlayerInteract(rig.chestInteraction(rig.player));

        assertEquals(RoundPhase.BUILDING, rig.controller.phase());
        assertTrue(
                rig.playerMessages.contains(
                        "Your build idea is: A dragon treehouse!"));
        rig.close();
    }

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
    void paperEmptyItemStacksAreSavedAsVacantSlots() {
        TestRig rig = new TestRig();
        ItemStack empty = new EmptyItemStack();
        rig.inventoryContents.set(new ItemStack[] {empty});
        rig.enderChestContents.set(new ItemStack[] {empty});
        rig.cursorItem.set(empty);

        rig.controller.start(rig.player);

        assertEquals(RoundPhase.PREPARING, rig.controller.phase());
        assertFalse(rig.persistentData.isEmpty());
        rig.controller.stop(rig.player);
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

    @Test
    void buildingAppliesPlotBorderAndRevealRestoresWorldDefault() {
        TestRig rig = new TestRig();

        rig.advanceTo(RoundPhase.BUILDING);

        assertNotNull(rig.playerWorldBorder.get());
        assertEquals(0.5, rig.borderCenterX.get());
        assertEquals(-2.5, rig.borderCenterZ.get());
        assertEquals(1.0, rig.borderSize.get());
        assertTrue(
                rig.consoleCommands.stream()
                        .allMatch(
                                command ->
                                        command.startsWith(
                                                "execute in minecraft:battle_world run tp ")));

        rig.runTimerTick();

        assertEquals(RoundPhase.REVEAL, rig.controller.phase());
        assertNull(rig.playerWorldBorder.get());
        rig.close();
    }

    @Test
    void adminStopRestoresWorldDefaultBorderDuringBuilding() {
        TestRig rig = new TestRig();
        rig.advanceTo(RoundPhase.BUILDING);
        assertNotNull(rig.playerWorldBorder.get());

        rig.controller.stop(rig.player);

        assertEquals(RoundPhase.IDLE, rig.controller.phase());
        assertNull(rig.playerWorldBorder.get());
        rig.close();
    }

    @Test
    void contestantBlockEventsEnforcePhasePlotAndVerticalLimits() {
        TestRig rig = new TestRig();
        Block insidePlot = rig.blockAt(0, 1, -3);

        rig.controller.start(rig.player);
        BlockBreakEvent preparingBreak = new BlockBreakEvent(insidePlot, rig.player);
        rig.controller.onContestantBlockBreak(preparingBreak);
        assertTrue(preparingBreak.isCancelled());

        rig.runBlockTick();
        rig.runTimerTick();
        rig.runTimerTick();
        assertEquals(RoundPhase.BUILDING, rig.controller.phase());

        BlockBreakEvent insideBreak = new BlockBreakEvent(insidePlot, rig.player);
        rig.controller.onContestantBlockBreak(insideBreak);
        assertFalse(insideBreak.isCancelled());

        Block outsidePlot = rig.blockAt(1, 1, -3);
        BlockBreakEvent outsideBreak = new BlockBreakEvent(outsidePlot, rig.player);
        rig.controller.onContestantBlockBreak(outsideBreak);
        assertTrue(outsideBreak.isCancelled());

        Block cap = rig.blockAt(0, 2, -3);
        BlockBreakEvent capBreak = new BlockBreakEvent(cap, rig.player);
        rig.controller.onContestantBlockBreak(capBreak);
        assertTrue(capBreak.isCancelled());

        BlockState replacedState =
                proxy(
                        BlockState.class,
                        (ignored, method, arguments) -> defaultValue(method.getReturnType()));
        BlockPlaceEvent outsidePlace =
                new BlockPlaceEvent(
                        outsidePlot,
                        replacedState,
                        insidePlot,
                        null,
                        rig.player,
                        true,
                        EquipmentSlot.HAND);
        rig.controller.onContestantBlockPlace(outsidePlace);
        assertTrue(outsidePlace.isCancelled());
        assertFalse(outsidePlace.canBuild());

        BlockState insideState =
                proxy(
                        BlockState.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getBlock")
                                        ? insidePlot
                                        : defaultValue(method.getReturnType()));
        BlockState outsideState =
                proxy(
                        BlockState.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getBlock")
                                        ? outsidePlot
                                        : defaultValue(method.getReturnType()));
        BlockMultiPlaceEvent crossingMultiPlace =
                new BlockMultiPlaceEvent(
                        List.of(insideState, outsideState),
                        insidePlot,
                        null,
                        rig.player,
                        true,
                        EquipmentSlot.HAND);
        rig.controller.onContestantBlockPlace(crossingMultiPlace);
        assertTrue(crossingMultiPlace.isCancelled());
        assertFalse(crossingMultiPlace.canBuild());
        rig.close();
    }

    @Test
    void contestantBucketsAndFluidFlowCannotCrossPlotBoundary() {
        TestRig rig = new TestRig();
        Block insidePlot = rig.blockAt(0, 1, -3);
        Block outsidePlot = rig.blockAt(1, 1, -3);
        rig.advanceTo(RoundPhase.BUILDING);

        PlayerBucketEmptyEvent outsideEmpty =
                new PlayerBucketEmptyEvent(
                        rig.player,
                        outsidePlot,
                        insidePlot,
                        BlockFace.EAST,
                        Material.WATER_BUCKET,
                        null,
                        EquipmentSlot.HAND);
        rig.controller.onContestantBucketEmpty(outsideEmpty);
        assertTrue(outsideEmpty.isCancelled());

        PlayerBucketFillEvent insideFill =
                new PlayerBucketFillEvent(
                        rig.player,
                        insidePlot,
                        insidePlot,
                        BlockFace.SELF,
                        Material.WATER_BUCKET,
                        null,
                        EquipmentSlot.HAND);
        rig.controller.onContestantBucketFill(insideFill);
        assertFalse(insideFill.isCancelled());

        BlockFromToEvent crossingFlow = new BlockFromToEvent(insidePlot, outsidePlot);
        rig.controller.onArenaFluidFlow(crossingFlow);
        assertTrue(crossingFlow.isCancelled());

        BlockFromToEvent insideFlow = new BlockFromToEvent(insidePlot, insidePlot);
        rig.controller.onArenaFluidFlow(insideFlow);
        assertFalse(insideFlow.isCancelled());
        rig.close();
    }

    @Test
    void activeArenaCancelsIndirectExplosionAndPistonMutations() {
        TestRig rig = new TestRig();
        Block arenaBlock = rig.blockAt(0, 1, -3);
        BlockState blockState =
                proxy(
                        BlockState.class,
                        (ignored, method, arguments) -> defaultValue(method.getReturnType()));
        Entity entity =
                proxy(
                        Entity.class,
                        (ignored, method, arguments) -> defaultValue(method.getReturnType()));
        rig.controller.start(rig.player);

        BlockExplodeEvent blockExplosion =
                new BlockExplodeEvent(
                        arenaBlock,
                        blockState,
                        List.of(arenaBlock),
                        1.0F,
                        ExplosionResult.DESTROY);
        rig.controller.onArenaBlockExplode(blockExplosion);
        assertTrue(blockExplosion.isCancelled());

        EntityExplodeEvent entityExplosion =
                new EntityExplodeEvent(
                        entity,
                        new Location(rig.world, 0, 1, -3),
                        List.of(arenaBlock),
                        1.0F,
                        ExplosionResult.DESTROY);
        rig.controller.onArenaEntityExplode(entityExplosion);
        assertTrue(entityExplosion.isCancelled());

        BlockPistonExtendEvent extend =
                new BlockPistonExtendEvent(
                        arenaBlock, List.of(arenaBlock), BlockFace.EAST);
        rig.controller.onArenaPistonExtend(extend);
        assertTrue(extend.isCancelled());

        BlockPistonRetractEvent retract =
                new BlockPistonRetractEvent(
                        arenaBlock, List.of(arenaBlock), BlockFace.WEST);
        rig.controller.onArenaPistonRetract(retract);
        assertTrue(retract.isCancelled());

        rig.controller.stop(rig.player);
        BlockPistonExtendEvent idleExtend =
                new BlockPistonExtendEvent(
                        arenaBlock, List.of(arenaBlock), BlockFace.EAST);
        rig.controller.onArenaPistonExtend(idleExtend);
        assertFalse(idleExtend.isCancelled());
        rig.close();
    }

    private static final class TestRig {
        private final AtomicReference<Runnable> blockTick = new AtomicReference<>();
        private final AtomicReference<Runnable> timerTick = new AtomicReference<>();
        private final AtomicBoolean playerOnline = new AtomicBoolean(true);
        private final AtomicBoolean failChunkLoads = new AtomicBoolean();
        private final AtomicBoolean failSaveData = new AtomicBoolean();
        private final AtomicBoolean failRunTask = new AtomicBoolean();
        private final AtomicBoolean failTaskBookPlacement = new AtomicBoolean();
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
        private final AtomicReference<WorldBorder> playerWorldBorder =
                new AtomicReference<>();
        private final AtomicReference<Double> borderCenterX = new AtomicReference<>();
        private final AtomicReference<Double> borderCenterZ = new AtomicReference<>();
        private final AtomicReference<Double> borderSize = new AtomicReference<>();
        private final AtomicReference<ItemStack> cursorItem = new AtomicReference<>();
        private final AtomicReference<String> placedTask = new AtomicReference<>();
        private final AtomicReference<ItemStack[]> inventoryContents =
                new AtomicReference<>(new ItemStack[2]);
        private final AtomicReference<ItemStack[]> enderChestContents =
                new AtomicReference<>(new ItemStack[3]);
        private final List<String> messages = new ArrayList<>();
        private final List<String> playerMessages = new ArrayList<>();
        private final List<String> spectatorMessages = new ArrayList<>();
        private final List<String> playerTitles = new ArrayList<>();
        private final List<String> spectatorTitles = new ArrayList<>();
        private final List<String> starterMessages = new ArrayList<>();
        private final List<String> consoleCommands = new ArrayList<>();
        private final Map<NamespacedKey, Object> persistentData = new HashMap<>();
        private final Map<NamespacedKey, Object> spectatorPersistentData = new HashMap<>();
        private final World world;
        private final PlayerInventory playerInventory;
        private final Inventory enderChest;
        private final PersistentDataContainer persistentDataContainer;
        private final PersistentDataContainer spectatorPersistentDataContainer;
        private final Block secretChestBlock;
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
            secretChestBlock =
                    proxy(
                            Block.class,
                            (ignored, method, arguments) -> {
                                return switch (method.getName()) {
                                    case "setType" -> {
                                        blockMutations.incrementAndGet();
                                        yield null;
                                    }
                                    case "getWorld" -> worldRef();
                                    case "getX" -> 2;
                                    case "getY" -> floorY + 1;
                                    case "getZ" -> 0;
                                    default -> defaultValue(method.getReturnType());
                                };
                            });
            world =
                    proxy(
                            World.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getSpawnLocation" -> new Location(worldRef(), 0, 1, 0);
                                        case "getName" -> ArenaWorldService.WORLD_NAME;
                                        case "getKey" ->
                                                new NamespacedKey(
                                                        "minecraft",
                                                        ArenaWorldService.WORLD_NAME);
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
                                        case "getBlockAt" -> secretChestBlock;
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
                                        case "setWorldBorder" -> {
                                            playerWorldBorder.set((WorldBorder) arguments[0]);
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
                                            throw new AssertionError(
                                                    "round teleports must use console commands");
                                        }
                                        case "sendMessage" -> {
                                            if (arguments[0] instanceof String message) {
                                                messages.add(message);
                                                playerMessages.add(message);
                                            }
                                            yield null;
                                        }
                                        case "sendTitle" -> {
                                            titles.incrementAndGet();
                                            playerTitles.add(
                                                    arguments[0] + " | " + arguments[1]);
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
                                            throw new AssertionError(
                                                    "round teleports must use console commands");
                                        }
                                        case "sendMessage" -> {
                                            if (arguments[0] instanceof String message) {
                                                messages.add(message);
                                                spectatorMessages.add(message);
                                            }
                                            yield null;
                                        }
                                        case "sendTitle" -> {
                                            titles.incrementAndGet();
                                            spectatorTitles.add(
                                                    arguments[0] + " | " + arguments[1]);
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            consoleStarter =
                    proxy(
                            ConsoleCommandSender.class,
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
                                            if (failRunTask.get()) {
                                                throw new IllegalStateException(
                                                        "test scheduler rejection");
                                            }
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
                                        case "createWorldBorder" -> newWorldBorder();
                                        case "getConsoleSender" -> consoleStarter;
                                        case "dispatchCommand" -> {
                                            String command = (String) arguments[1];
                                            consoleCommands.add(command);
                                            applyTeleportCommand(command);
                                            yield true;
                                        }
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
                            Logger.getAnonymousLogger(),
                            ignored -> 0,
                            prompt -> {
                                if (failTaskBookPlacement.get()) {
                                    throw new IllegalStateException(
                                            "test cosmetic book failure");
                                }
                                placedTask.set(prompt);
                            });
            assertNotNull(blockTick.get());
            assertNotNull(timerTick.get());
        }

        private WorldBorder newWorldBorder() {
            return proxy(
                    WorldBorder.class,
                    (ignored, method, arguments) -> {
                        switch (method.getName()) {
                            case "setCenter" -> {
                                borderCenterX.set((double) arguments[0]);
                                borderCenterZ.set((double) arguments[1]);
                            }
                            case "setSize" -> borderSize.set((double) arguments[0]);
                            default -> {
                                // Damage and warning settings do not change test geometry.
                            }
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private void applyTeleportCommand(String command) {
            String[] parts = command.split(" ");
            assertEquals(11, parts.length);
            assertEquals("execute", parts[0]);
            assertEquals("in", parts[1]);
            assertEquals("minecraft:battle_world", parts[2]);
            assertEquals("run", parts[3]);
            assertEquals("tp", parts[4]);
            Location destination =
                    new Location(
                            world,
                            Double.parseDouble(parts[6]),
                            Double.parseDouble(parts[7]),
                            Double.parseDouble(parts[8]),
                            Float.parseFloat(parts[9]),
                            Float.parseFloat(parts[10]));
            if (parts[5].equals("BuilderKid")) {
                lastTeleport.set(destination);
                return;
            }
            assertEquals("Parent", parts[5]);
            spectatorLocation.set(destination);
        }

        private Block blockAt(int x, int y, int z) {
            return proxy(
                    Block.class,
                    (ignored, method, arguments) ->
                            switch (method.getName()) {
                                case "getWorld" -> world;
                                case "getX" -> x;
                                case "getY" -> y;
                                case "getZ" -> z;
                                default -> defaultValue(method.getReturnType());
                            });
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

        private PlayerInteractEvent chestInteraction(Player interactingPlayer) {
            return chestInteraction(interactingPlayer, EquipmentSlot.HAND);
        }

        private PlayerInteractEvent chestInteraction(
                Player interactingPlayer, EquipmentSlot hand) {
            return new PlayerInteractEvent(
                    interactingPlayer,
                    Action.RIGHT_CLICK_BLOCK,
                    null,
                    secretChestBlock,
                    BlockFace.UP,
                    hand);
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

    private static final class EmptyItemStack extends ItemStack {
        private EmptyItemStack() {
            super();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ItemStack clone() {
            return this;
        }

        @Override
        public byte[] serializeAsBytes() {
            throw new AssertionError("empty item stacks must not be serialized");
        }
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
