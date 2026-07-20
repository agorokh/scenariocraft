package io.github.agorokh.scenariocraft.buildbattle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Applies the validated phase machine to Paper players, timers, and arena work. */
public final class RoundController implements BattleRound, Listener, AutoCloseable {
    private static final int MINIMUM_DEBUG_PLOTS = 2;
    private static final int TELEPORT_FADE_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 50;
    private static final int INVENTORY_SNAPSHOT_VERSION = 1;
    private static final int MAX_SNAPSHOT_SECTION_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SNAPSHOT_SLOTS = 128;
    private static final double TELEPORT_CONFIRMATION_EPSILON = 1.0E-6;
    private static final long[] TELEPORT_CONFIRMATION_DELAYS = {1L, 4L, 15L};
    private static final Pattern COMMAND_WORLD_KEY =
            Pattern.compile("[a-z0-9._-]+:[a-z0-9/._-]+");

    private final Plugin plugin;
    private final Server server;
    private final BattleSettings settings;
    private final ArenaWorld arena;
    private final BatchedBlockEditor blockEditor;
    private final Logger logger;
    private final PickerSelector pickerSelector;
    private final TaskDeck taskDeck;
    private final Consumer<String> taskBookPlacer;
    private final RoundStateMachine state = new RoundStateMachine();
    private final Map<UUID, Contestant> contestants = new LinkedHashMap<>();
    private final Map<UUID, Spectator> revealSpectators = new LinkedHashMap<>();
    private final Set<UUID> strandedArenaPlayers = new LinkedHashSet<>();
    private final Map<UUID, Long> teleportAttempts = new LinkedHashMap<>();
    private final Map<UUID, Location> expectedTeleportDestinations =
            new LinkedHashMap<>();
    private final Set<UUID> pendingPlotEntries = new LinkedHashSet<>();
    private final NamespacedKey inventorySnapshotKey;
    private final NamespacedKey teleportRecoveryKey;
    private final BossBar buildBossBar;
    private final BukkitTask timerTask;
    private List<PlotBounds> plots = List.of();
    private RoundTimer timer;
    private CommandSender roundStarter;
    private UUID currentPickerId;
    private String currentPickerName;
    private String currentTask;
    private boolean taskRevealed;
    private boolean closed;
    private boolean movingContestantsToPlots;
    private boolean plotEntryFailed;
    private boolean awaitingPlotEntries;
    private long nextTeleportAttempt;

    public RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger) {
        this(
                plugin,
                settings,
                arena,
                blockEditor,
                logger,
                bound -> ThreadLocalRandom.current().nextInt(bound),
                ignored -> placeTaskBook(arena));
    }

    RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger,
            IntUnaryOperator randomIndex,
            Consumer<String> taskBookPlacer) {
        Objects.requireNonNull(plugin, "plugin");
        this.plugin = plugin;
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.blockEditor = Objects.requireNonNull(blockEditor, "blockEditor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pickerSelector = new PickerSelector(randomIndex);
        this.taskDeck = new TaskDeck(settings.tasks(), randomIndex);
        this.taskBookPlacer = Objects.requireNonNull(taskBookPlacer, "taskBookPlacer");
        this.inventorySnapshotKey =
                new NamespacedKey(plugin, "round-inventory-snapshot");
        this.teleportRecoveryKey =
                new NamespacedKey(plugin, "teleport-recovery-pending");
        this.buildBossBar =
                server.createBossBar(
                        "Build time", BarColor.BLUE, BarStyle.SOLID);
        buildBossBar.setVisible(false);
        server.getPluginManager().registerEvents(this, plugin);
        this.timerTask =
                server.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Player player : server.getOnlinePlayers()) {
            boolean hadPendingInventory =
                    player.getPersistentDataContainer()
                            .has(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
            restorePendingInventory(player);
            if (!hadPendingInventory && hasPendingTeleportRecovery(player)) {
                retryStrandedExit(player);
            }
        }
    }

    @Override
    public RoundPhase phase() {
        return state.phase();
    }

    @Override
    public void start(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (closed) {
            sender.sendMessage("ScenarioCraft is taking a short break right now.");
            return;
        }
        if (phase() != RoundPhase.IDLE) {
            sender.sendMessage(
                    "A Build Battle is already in " + friendlyPhase(phase()) + ".");
            return;
        }
        if (blockEditor.isBusy()) {
            sender.sendMessage(
                    "The arena is already getting ready — just a few more blocks to go!");
            return;
        }

        List<Player> players =
                server.getOnlinePlayers().stream()
                        .map(Player.class::cast)
                        .filter(player -> !settings.isExempt(player.getName()))
                        .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        if (players.size() > settings.arena().maxPlots()) {
            sender.sendMessage(
                    "There are more builders than plots right now. Please ask a grown-up helper to adjust the arena.");
            return;
        }
        int plotCount = Math.max(MINIMUM_DEBUG_PLOTS, players.size());
        ArenaSettings arenaSettings = settings.arena();
        plots =
                PlotGeometry.aroundHub(
                        arena.world().getSpawnLocation().getBlockX(),
                        arena.world().getSpawnLocation().getBlockZ(),
                        plotCount,
                        arenaSettings.plotSize(),
                        arenaSettings.plotSpacing());
        List<PlotBoundary> boundaries;
        try {
            boundaries =
                    plots.stream()
                            .map(
                                    plot ->
                                            PlotBoundary.forPlot(
                                                    plot,
                                                    arena.floorY(),
                                                    arenaSettings.wallHeight()))
                            .toList();
        } catch (RuntimeException failure) {
            logger.log(Level.SEVERE, "Could not prepare arena boundaries", failure);
            plots = List.of();
            sender.sendMessage(
                    "The arena could not get ready this time. Please ask a grown-up helper to check the server.");
            return;
        }
        contestants.clear();
        try {
            for (int index = 0; index < players.size(); index++) {
                Player player = players.get(index);
                player.closeInventory();
                InventorySnapshot inventorySnapshot = snapshotPlayerInventory(player);
                persistInventorySnapshot(player, inventorySnapshot);
                contestants.put(
                        player.getUniqueId(),
                        new Contestant(
                                player.getUniqueId(),
                                plots.get(index),
                                boundaries.get(index),
                                inventorySnapshot));
                strandedArenaPlayers.remove(player.getUniqueId());
            }
        } catch (RuntimeException failure) {
            for (Player player : players) {
                player.getPersistentDataContainer().remove(inventorySnapshotKey);
                try {
                    player.saveData();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            contestants.clear();
            plots = List.of();
            logger.log(Level.SEVERE, "Could not save round inventory snapshots", failure);
            sender.sendMessage(
                    "The battle could not save everyone's items safely. Please ask a grown-up helper to check the server.");
            return;
        }

        transitionTo(RoundPhase.PREPARING);
        logger.info(
                "Active-round protection enabled across "
                        + arena.world().getName()
                        + ": explosions, pistons, dispensers, fire, fluids, and entity block changes are contained until IDLE.");
        roundStarter = sender;
        forEachOnlineContestant(this::prepareContestant);
        if (players.isEmpty()) {
            sender.sendMessage("Starting a two-plot practice round.");
        } else if (players.size() == 1) {
            sender.sendMessage("Starting with one builder and one extra practice plot.");
        } else {
            sender.sendMessage("Starting Build Battle for " + players.size() + " builders!");
        }
        broadcast("Build Battle is getting the arena ready in safe little batches!");

        long mutations;
        try {
            mutations =
                    blockEditor.enqueueArena(
                            plots,
                            arena.floorY(),
                            arenaSettings.wallHeight(),
                            secretChest(),
                            completedMutations -> arenaResetComplete(completedMutations),
                            this::arenaWorkFailed);
        } catch (RuntimeException failure) {
            arenaWorkFailed(failure);
            return;
        }
        if (phase() != RoundPhase.PREPARING || !blockEditor.isBusy()) {
            return;
        }
        logger.info(
                "Arena build queued: "
                        + plots.size()
                        + " plots in "
                        + arena.world().getName()
                        + ", "
                        + mutations
                        + " block mutations at "
                        + arenaSettings.blocksPerTick()
                        + " per tick.");
    }

    @Override
    public void stop(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (phase() == RoundPhase.IDLE) {
            sender.sendMessage("There is no active Build Battle to stop.");
            return;
        }
        RoundPhase stoppedPhase = phase();
        abortRound();
        sender.sendMessage(
                "Build Battle stopped cleanly from " + friendlyPhase(stoppedPhase) + ".");
        broadcast("Build Battle stopped. Everyone is safe at the hub.");
        logger.info("Round stopped from " + stoppedPhase + " and returned to IDLE.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        notifyJoiningOperatorOfPendingTeleportFailures(player);
        Contestant contestant = contestants.get(player.getUniqueId());
        if (contestant != null && phase() != RoundPhase.IDLE) {
            try {
                persistInventorySnapshot(player, contestant.inventorySnapshot());
            } catch (RuntimeException failure) {
                logger.log(
                        Level.SEVERE,
                        "Could not re-save round inventory for " + player.getName(),
                        failure);
                player.sendMessage(
                        "Your items could not be saved safely. Please ask a grown-up helper.");
            }
            applyCurrentPhase(player, contestant);
            return;
        }
        boolean hadPendingInventory =
                player.getPersistentDataContainer()
                        .has(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
        restorePendingInventory(player);
        if (!hadPendingInventory
                && (strandedArenaPlayers.contains(player.getUniqueId())
                        || hasPendingTeleportRecovery(player))) {
            retryStrandedExit(player);
            return;
        }
        if (phase() == RoundPhase.REVEAL) {
            Spectator spectator =
                    revealSpectators.computeIfAbsent(
                            player.getUniqueId(),
                            ignored ->
                                    new Spectator(
                                            player.getUniqueId(),
                                            player.getLocation().clone(),
                                            player.getGameMode()));
            moveSpectatorToTour(player, spectator);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean abandonedPlotEntry =
                pendingPlotEntries.remove(player.getUniqueId());
        buildBossBar.removePlayer(player);
        Contestant contestant = contestants.get(player.getUniqueId());
        if (contestant != null) {
            restoreContestantToHub(player, contestant);
        }
        Spectator spectator = revealSpectators.get(player.getUniqueId());
        if (spectator != null) {
            restoreSpectator(player, spectator);
        }
        if (abandonedPlotEntry) {
            finishBeginBuildingIfReady();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if ((phase() != RoundPhase.IDLE && contestants.containsKey(playerId))
                || (strandedArenaPlayers.contains(playerId)
                        && event.getPlayer().getWorld() == arena.world())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            if ((phase() != RoundPhase.IDLE
                            && contestants.containsKey(playerId))
                    || (strandedArenaPlayers.contains(playerId)
                            && player.getWorld() == arena.world())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block clicked = event.getClickedBlock();
            if (clicked != null
                    && isActiveArenaBlock(clicked)
                    && !mayContestantEdit(event.getPlayer(), clicked)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (!isSecretChest(clicked)) {
            boolean editableClickedBlock =
                    mayContestantEdit(event.getPlayer(), clicked);
            boolean editableAdjacentBlock =
                    mayContestantEdit(
                            event.getPlayer(),
                            clicked.getRelative(event.getBlockFace()));
            if (!editableClickedBlock) {
                event.setUseInteractedBlock(Result.DENY);
            }
            if (!editableClickedBlock
                    && editableAdjacentBlock
                    && event.useItemInHand() != Result.DENY) {
                event.setUseItemInHand(Result.ALLOW);
            } else if (!editableClickedBlock) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        if (phase() != RoundPhase.NOTE_PICK) {
            event.setCancelled(true);
            player.sendMessage(
                    "The secret chest opens during note pick. The next mystery is coming soon!");
            return;
        }
        if (currentPickerId == null || !currentPickerId.equals(player.getUniqueId())) {
            event.setCancelled(true);
            String pickerName =
                    currentPickerName == null ? "the note picker" : currentPickerName;
            player.sendMessage(
                    "This secret note belongs to "
                            + pickerName
                            + " this round. You'll see the idea together soon!");
            return;
        }
        if (!revealCurrentTask()) {
            event.setCancelled(true);
            player.sendMessage("The secret note is already open — build time is starting!");
            return;
        }

        try {
            server.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (phase() == RoundPhase.NOTE_PICK && taskRevealed) {
                                    beginBuilding();
                                }
                            });
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Could not schedule the post-note transition; continuing immediately",
                    failure);
            if (phase() == RoundPhase.NOTE_PICK && taskRevealed) {
                beginBuilding();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSecretChestBreak(BlockBreakEvent event) {
        if (!isSecretChest(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer()
                .sendMessage(
                        "That secret chest is part of the game, so it stays right here!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBlockBreak(BlockBreakEvent event) {
        if (isSecretChest(event.getBlock())) {
            return;
        }
        if (!mayContestantEdit(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBlockPlace(BlockPlaceEvent event) {
        boolean mayPlace =
                event instanceof BlockMultiPlaceEvent multiPlace
                        ? multiPlace.getReplacedBlockStates().stream()
                                .map(org.bukkit.block.BlockState::getBlock)
                                .allMatch(block -> mayContestantEdit(event.getPlayer(), block))
                        : mayContestantEdit(event.getPlayer(), event.getBlockPlaced());
        if (!mayPlace) {
            event.setCancelled(true);
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockDispense(BlockDispenseEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockSpread(BlockSpreadEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockBurn(BlockBurnEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (isActiveArenaBlock(event.getBlock())
                && (player == null
                        || !mayContestantEdit(player, event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (event.getBlocks().stream()
                .map(org.bukkit.block.BlockState::getBlock)
                .anyMatch(
                        block ->
                                player == null
                                        ? isActiveArenaBlock(block)
                                        : !mayContestantEdit(player, block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaStructureGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        if (event.getBlocks().stream()
                .map(org.bukkit.block.BlockState::getBlock)
                .anyMatch(
                        block ->
                                player == null
                                        ? isActiveArenaBlock(block)
                                        : !mayContestantEdit(player, block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock =
                event.getBlock().getRelative(event.getBlockFace());
        if (player == null
                ? isActiveArenaBlock(placedBlock)
                : !mayContestantEdit(player, placedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock =
                event.getBlock().getRelative(event.getBlockFace());
        if (player == null
                ? isActiveArenaBlock(placedBlock)
                : !mayContestantEdit(player, placedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isActiveArenaBlock(event.getBlock())
                && (!(event.getEntity() instanceof FallingBlock fallingBlock)
                        || !isSameEditablePlot(
                                fallingBlock.getSourceLoc(),
                                event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantTeleport(PlayerTeleportEvent event) {
        Contestant contestant = contestants.get(event.getPlayer().getUniqueId());
        Location destination = event.getTo();
        boolean plotContainmentActive =
                phase() == RoundPhase.BUILDING
                        || (phase() == RoundPhase.NOTE_PICK
                                && awaitingPlotEntries);
        if (!plotContainmentActive
                || contestant == null
                || destination == null
                || matchesExpectedTeleport(event.getPlayer(), destination)) {
            return;
        }
        if (destination.getWorld() != arena.world()
                || !contestant.boundary()
                        .containsEditableBlock(
                                destination.getBlockX(),
                                destination.getBlockY(),
                                destination.getBlockZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockForm(BlockFormEvent event) {
        if (isActiveArenaBlock(event.getBlock())
                && !isEditableByAnyContestant(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityBlockForm(EntityBlockFormEvent event) {
        if (isActiveArenaBlock(event.getBlock())
                && (!(event.getEntity() instanceof Player player)
                        || !mayContestantEdit(player, event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaLeavesDecay(LeavesDecayEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!mayContestantEdit(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBucketFill(PlayerBucketFillEvent event) {
        if (!mayContestantEdit(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaFluidFlow(BlockFromToEvent event) {
        if (isActiveArenaBlock(event.getBlock())
                && !mayFluidFlow(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockExplode(BlockExplodeEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityExplode(EntityExplodeEvent event) {
        if (phase() != RoundPhase.IDLE
                && event.getLocation().getWorld() == arena.world()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaPistonExtend(BlockPistonExtendEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaPistonRetract(BlockPistonRetractEvent event) {
        if (isActiveArenaBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        timerTask.cancel();
        blockEditor.cancel();
        timer = null;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        restoreRoundPlayers();
        contestants.clear();
        revealSpectators.clear();
        strandedArenaPlayers.clear();
        teleportAttempts.clear();
        expectedTeleportDestinations.clear();
        pendingPlotEntries.clear();
        awaitingPlotEntries = false;
        roundStarter = null;
        resetNotePickState();
        plots = List.of();
    }

    private void arenaResetComplete(long mutations) {
        if (phase() != RoundPhase.PREPARING) {
            return;
        }
        logger.info(
                "Arena build complete: "
                        + plots.size()
                        + " plots in "
                        + arena.world().getName()
                        + " ("
                        + mutations
                        + " block mutations).");
        transitionTo(RoundPhase.GATHERING);
        forEachOnlineContestant(this::moveToHub);
        broadcast(
                "Gather at the hub! Your build idea arrives in "
                        + settings.timings().gatherSeconds()
                        + " seconds.");
        timer = RoundTimer.start(settings.timings().gatherSeconds());
    }

    private void tick() {
        if (timer == null || phase() == RoundPhase.IDLE || phase() == RoundPhase.PREPARING) {
            return;
        }
        timer = timer.tick();
        if (phase() == RoundPhase.BUILDING) {
            updateBuildBossBar();
            timer.buildWarning().ifPresent(this::broadcast);
        } else if ((phase() == RoundPhase.GATHERING || phase() == RoundPhase.NOTE_PICK)
                && timer.shouldAnnounceShortCountdown()) {
            broadcast(
                    friendlyPhase(phase())
                            + ": "
                            + timer.remainingSeconds()
                            + " seconds left.");
        }
        if (!timer.isComplete()) {
            return;
        }

        switch (phase()) {
            case GATHERING -> beginNotePick();
            case NOTE_PICK -> {
                broadcast("Time is up — the secret note opened itself for everyone!");
                revealCurrentTask();
                beginBuilding();
            }
            case BUILDING -> beginReveal();
            case REVEAL -> completeRound();
            case IDLE, PREPARING -> throw new IllegalStateException(
                    "untimed phase unexpectedly owned a countdown");
        }
    }

    private void beginNotePick() {
        transitionTo(RoundPhase.NOTE_PICK);
        timer = RoundTimer.start(settings.timings().noteSeconds());
        currentTask = taskDeck.draw();
        taskRevealed = false;
        try {
            taskBookPlacer.accept(currentTask);
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Could not prepare the cosmetic secret task book; continuing with title and chat",
                    failure);
        }

        PickerSelector.Candidate picker =
                pickerSelector
                        .select(
                                contestants.values().stream()
                                        .map(contestant -> server.getPlayer(contestant.playerId()))
                                        .filter(Objects::nonNull)
                                        .filter(Player::isOnline)
                                        .map(
                                                player ->
                                                        new PickerSelector.Candidate(
                                                                player.getUniqueId(),
                                                                player.getName(),
                                                                settings.isExempt(
                                                                        player.getName())))
                                        .toList())
                        .orElse(null);
        if (picker == null) {
            broadcast(
                    "No builder is at the hub to open the secret note, so it opened itself!");
            revealCurrentTask();
            beginBuilding();
            return;
        }

        currentPickerId = picker.playerId();
        currentPickerName = picker.playerName();
        broadcast(
                currentPickerName
                        + " is the secret-note picker! The chest is waiting at the hub.");
        for (Player player : server.getOnlinePlayers()) {
            sendPickerTitle(player);
        }
    }

    private boolean revealCurrentTask() {
        if (taskRevealed || currentTask == null) {
            return false;
        }
        taskRevealed = true;
        timer = null;
        broadcast("Your build idea is: " + currentTask + "!");
        for (Player player : server.getOnlinePlayers()) {
            sendTaskTitle(player);
        }
        return true;
    }

    private void beginBuilding() {
        if (awaitingPlotEntries) {
            return;
        }
        plotEntryFailed = false;
        awaitingPlotEntries = true;
        pendingPlotEntries.clear();
        movingContestantsToPlots = true;
        try {
            forEachOnlineContestant(this::moveToPlot);
        } finally {
            movingContestantsToPlots = false;
        }
        if (plotEntryFailed) {
            abortAfterPlotEntryFailure();
            return;
        }
        finishBeginBuildingIfReady();
    }

    private void finishBeginBuildingIfReady() {
        if (movingContestantsToPlots
                || plotEntryFailed
                || !awaitingPlotEntries
                || !pendingPlotEntries.isEmpty()
                || phase() != RoundPhase.NOTE_PICK) {
            return;
        }
        awaitingPlotEntries = false;
        transitionTo(RoundPhase.BUILDING);
        timer = RoundTimer.start(settings.timings().buildSeconds());
        buildBossBar.setVisible(true);
        updateBuildBossBar();
        broadcast("Build time! Have fun making something only you could imagine.");
    }

    private void beginReveal() {
        transitionTo(RoundPhase.REVEAL);
        timer = null;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        revealSpectators.clear();
        for (Player player : server.getOnlinePlayers()) {
            Contestant contestant = contestants.get(player.getUniqueId());
            if (contestant != null) {
                moveContestantToTour(player, contestant);
                continue;
            }
            Spectator spectator =
                    new Spectator(
                            player.getUniqueId(),
                            player.getLocation().clone(),
                            player.getGameMode());
            revealSpectators.put(player.getUniqueId(), spectator);
            moveSpectatorToTour(player, spectator);
        }
        broadcast("Time to reveal the builds! The walls are coming down safely.");

        ArenaSettings arenaSettings = settings.arena();
        long mutations;
        try {
            mutations =
                    blockEditor.enqueueWallRemoval(
                            plots,
                            arena.floorY(),
                            arenaSettings.wallHeight(),
                            this::wallRemovalComplete,
                            this::arenaWorkFailed);
        } catch (RuntimeException failure) {
            arenaWorkFailed(failure);
            return;
        }
        if (phase() != RoundPhase.REVEAL || !blockEditor.isBusy()) {
            return;
        }
        logger.info(
                "Arena reveal queued: "
                        + plots.size()
                        + " plots, "
                        + mutations
                        + " block mutations at "
                        + arenaSettings.blocksPerTick()
                        + " per tick.");
    }

    private void wallRemovalComplete(long mutations) {
        if (phase() != RoundPhase.REVEAL) {
            return;
        }
        logger.info(
                "Arena reveal complete: "
                        + plots.size()
                        + " plots ("
                        + mutations
                        + " block mutations).");
        broadcast(
                "The walls are down! Enjoy the build tour for "
                        + settings.timings().revealLingerSeconds()
                        + " seconds.");
        timer = RoundTimer.start(settings.timings().revealLingerSeconds());
    }

    private void completeRound() {
        restoreRoundPlayers();
        transitionTo(RoundPhase.IDLE);
        timer = null;
        pendingPlotEntries.clear();
        awaitingPlotEntries = false;
        contestants.clear();
        revealSpectators.clear();
        roundStarter = null;
        resetNotePickState();
        plots = List.of();
        broadcast("Build Battle complete — amazing creating, everyone!");
        logger.info("Round complete: returned to IDLE.");
    }

    private void abortRound() {
        blockEditor.cancel();
        timer = null;
        pendingPlotEntries.clear();
        awaitingPlotEntries = false;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        restoreRoundPlayers();
        transitionTo(RoundPhase.IDLE);
        contestants.clear();
        revealSpectators.clear();
        roundStarter = null;
        resetNotePickState();
        plots = List.of();
    }

    private void arenaWorkFailed(Throwable failure) {
        if (phase() == RoundPhase.IDLE) {
            return;
        }
        CommandSender starter = roundStarter;
        logger.log(Level.SEVERE, "Build Battle arena work failed; aborting round", failure);
        abortRound();
        String message =
                "The arena could not get ready this time. Please ask a grown-up helper to check the server.";
        broadcast(message);
        if (starter != null && !(starter instanceof Player)) {
            starter.sendMessage(message);
        }
    }

    private void transitionTo(RoundPhase next) {
        RoundPhase previous = phase();
        state.transitionTo(next);
        logger.info("Round phase changed: " + previous + " -> " + next + ".");
    }

    private void updateBuildBossBar() {
        if (timer == null) {
            return;
        }
        buildBossBar.setProgress(timer.remainingFraction());
        buildBossBar.setTitle("Build time: " + formatTime(timer.remainingSeconds()));
    }

    private void applyCurrentPhase(Player player, Contestant contestant) {
        clearRoundInventory(player);
        switch (phase()) {
            case PREPARING, GATHERING -> moveToHub(player, contestant);
            case NOTE_PICK -> {
                if (taskRevealed && awaitingPlotEntries) {
                    moveToPlot(player, contestant);
                } else {
                    moveToHub(player, contestant);
                }
                if (taskRevealed && !awaitingPlotEntries) {
                    sendTaskTitle(player);
                } else if (!taskRevealed) {
                    sendPickerTitle(player);
                }
            }
            case BUILDING -> moveToPlot(player, contestant);
            case REVEAL -> moveContestantToTour(player, contestant);
            case IDLE -> {
                // The caller excludes IDLE; retaining this arm keeps the phase handling exhaustive.
            }
        }
    }

    private void moveToHub(Player player, Contestant contestant) {
        resetPersonalBorder(player);
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                hubLocation(),
                () -> buildBossBar.removePlayer(player),
                () -> {
                    if (phase() == RoundPhase.BUILDING) {
                        constrainContestantAfterFailedExit(player, contestant);
                    }
                    buildBossBar.removePlayer(player);
                });
    }

    private void moveToPlot(Player player, Contestant contestant) {
        if (awaitingPlotEntries) {
            pendingPlotEntries.add(player.getUniqueId());
        }
        clearRoundInventory(player);
        resetPersonalBorder(player);
        player.setGameMode(GameMode.ADVENTURE);
        PlotBounds plot = contestant.plot();
        teleport(
                player,
                new Location(
                        arena.world(),
                        plot.centerX() + 0.5,
                        arena.floorY() + 1.0,
                        plot.centerZ() + 0.5),
                () -> {
                    applyPersonalBorder(player, contestant);
                    player.setGameMode(GameMode.CREATIVE);
                    buildBossBar.addPlayer(player);
                    if (pendingPlotEntries.remove(player.getUniqueId())) {
                        finishBeginBuildingIfReady();
                    }
                },
                () -> {
                    resetPersonalBorder(player);
                    player.setGameMode(GameMode.ADVENTURE);
                    strandedArenaPlayers.add(player.getUniqueId());
                    buildBossBar.removePlayer(player);
                    pendingPlotEntries.remove(player.getUniqueId());
                    if (phase() == RoundPhase.BUILDING
                            || (phase() == RoundPhase.NOTE_PICK
                                    && awaitingPlotEntries)) {
                        plotEntryFailed = true;
                        if (!movingContestantsToPlots) {
                            abortAfterPlotEntryFailure();
                        }
                    }
                });
    }

    private void abortAfterPlotEntryFailure() {
        if (phase() != RoundPhase.BUILDING
                && !(phase() == RoundPhase.NOTE_PICK
                        && awaitingPlotEntries)) {
            return;
        }
        awaitingPlotEntries = false;
        pendingPlotEntries.clear();
        logger.severe(
                "A contestant could not enter their plot; aborting the round safely.");
        abortRound();
        broadcast(
                "The plots could not open safely this time. Please ask a grown-up helper to check the server.");
    }

    private void prepareContestant(Player player, Contestant contestant) {
        clearRoundInventory(player);
        moveToHub(player, contestant);
    }

    private void moveContestantToTour(Player player, Contestant contestant) {
        clearRoundInventory(player);
        resetPersonalBorder(player);
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                tourLocation(),
                () -> {},
                () -> {
                    if (phase() == RoundPhase.REVEAL
                            && contestants.get(player.getUniqueId()) == contestant) {
                        constrainContestantAfterFailedExit(player, contestant);
                    } else {
                        resetPersonalBorder(player);
                    }
                });
    }

    private void restoreRoundPlayers() {
        forEachOnlineContestant(this::restoreContestantToHub);
        for (Spectator spectator : revealSpectators.values()) {
            Player player = server.getPlayer(spectator.playerId());
            if (player != null && player.isOnline()) {
                restoreSpectator(player, spectator);
            }
        }
    }

    private void restoreContestantToHub(Player player, Contestant contestant) {
        buildBossBar.removePlayer(player);
        resetPersonalBorder(player);
        strandedArenaPlayers.add(player.getUniqueId());
        persistTeleportRecovery(player);
        boolean inventoryRestored = false;
        try {
            restoreInventorySnapshot(player, contestant.inventorySnapshot());
            inventoryRestored = true;
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "Could not restore round inventory for " + player.getName(),
                    failure);
            player.sendMessage(
                    "Your saved items need a grown-up helper before the next battle.");
            player.setGameMode(GameMode.ADVENTURE);
        }
        boolean restored = inventoryRestored;
        teleport(
                player,
                hubLocation(),
                () -> {},
                () -> {
                    strandedArenaPlayers.add(player.getUniqueId());
                    if (!restored) {
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                });
    }

    private void clearRoundInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.updateInventory();
    }

    private void moveSpectatorToTour(Player player, Spectator ignored) {
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                tourLocation(),
                () -> {},
                () -> player.setGameMode(GameMode.ADVENTURE));
    }

    private void restoreSpectator(Player player, Spectator spectator) {
        teleport(
                player,
                spectator.originalLocation(),
                () -> player.setGameMode(spectator.originalGameMode()),
                () -> player.setGameMode(GameMode.ADVENTURE));
    }

    private void forEachOnlineContestant(OnlineContestantAction action) {
        for (Contestant contestant : contestants.values()) {
            Player player = server.getPlayer(contestant.playerId());
            if (player != null && player.isOnline()) {
                action.accept(player, contestant);
            }
        }
    }

    private void broadcast(String message) {
        for (Player player : server.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void sendPickerTitle(Player player) {
        if (currentPickerId == null || currentPickerName == null) {
            return;
        }
        String subtitle =
                currentPickerId.equals(player.getUniqueId())
                        ? "Open the chest at the hub!"
                        : "They'll reveal the build idea!";
        player.sendTitle(
                currentPickerName + " has the secret note!",
                subtitle,
                TELEPORT_FADE_TICKS,
                TITLE_STAY_TICKS,
                TELEPORT_FADE_TICKS);
    }

    private void sendTaskTitle(Player player) {
        if (currentTask == null) {
            return;
        }
        player.sendTitle(
                "Build idea!",
                currentTask,
                TELEPORT_FADE_TICKS,
                TITLE_STAY_TICKS,
                TELEPORT_FADE_TICKS);
    }

    private void resetNotePickState() {
        currentPickerId = null;
        currentPickerName = null;
        currentTask = null;
        taskRevealed = false;
    }

    private Location hubLocation() {
        Location spawn = arena.world().getSpawnLocation();
        return new Location(
                arena.world(),
                spawn.getBlockX() + 0.5,
                arena.floorY() + 1.0,
                spawn.getBlockZ() + 0.5);
    }

    private SecretChestPosition secretChest() {
        return SecretChestPosition.atHub(arena);
    }

    private boolean isSecretChest(Block block) {
        return block.getWorld() == arena.world() && secretChest().matches(block);
    }

    private boolean isActiveArenaBlock(Block block) {
        return phase() != RoundPhase.IDLE && block.getWorld() == arena.world();
    }

    private boolean mayContestantEdit(Player player, Block block) {
        Contestant contestant = contestants.get(player.getUniqueId());
        if (contestant != null) {
            if (block.getWorld() != arena.world()) {
                return false;
            }
            return PlotEditPolicy.mayEdit(
                    phase(),
                    contestant.boundary(),
                    block.getX(),
                    block.getY(),
                    block.getZ());
        }
        if (strandedArenaPlayers.contains(player.getUniqueId())
                && block.getWorld() == arena.world()) {
            return false;
        }
        return !isActiveArenaBlock(block);
    }

    private boolean mayFluidFlow(Block source, Block destination) {
        if (phase() != RoundPhase.BUILDING
                || source.getWorld() != arena.world()
                || destination.getWorld() != arena.world()) {
            return false;
        }
        for (Contestant contestant : contestants.values()) {
            PlotBoundary boundary = contestant.boundary();
            if (boundary.containsEditableBlock(
                            source.getX(), source.getY(), source.getZ())
                    && boundary.containsEditableBlock(
                            destination.getX(), destination.getY(), destination.getZ())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameEditablePlot(Location source, Block target) {
        if (phase() != RoundPhase.BUILDING
                || source == null
                || source.getWorld() != arena.world()
                || target.getWorld() != arena.world()) {
            return false;
        }
        return contestants.values().stream()
                .map(Contestant::boundary)
                .anyMatch(
                        boundary ->
                                boundary.containsEditableBlock(
                                                source.getBlockX(),
                                                source.getBlockY(),
                                                source.getBlockZ())
                                        && boundary.containsEditableBlock(
                                                target.getX(),
                                                target.getY(),
                                                target.getZ()));
    }

    private boolean isEditableByAnyContestant(Block block) {
        if (phase() != RoundPhase.BUILDING
                || block.getWorld() != arena.world()) {
            return false;
        }
        return contestants.values().stream()
                .map(Contestant::boundary)
                .anyMatch(
                        boundary ->
                                boundary.containsEditableBlock(
                                        block.getX(),
                                        block.getY(),
                                        block.getZ()));
    }

    private boolean matchesExpectedTeleport(
            Player player, Location destination) {
        Location expected =
                expectedTeleportDestinations.get(player.getUniqueId());
        return expected != null
                && expected.getWorld() == destination.getWorld()
                && Math.abs(expected.getX() - destination.getX())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(expected.getY() - destination.getY())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(expected.getZ() - destination.getZ())
                        <= TELEPORT_CONFIRMATION_EPSILON;
    }

    private void applyPersonalBorder(Player player, Contestant contestant) {
        PlotBoundary boundary = contestant.boundary();
        WorldBorder border = server.createWorldBorder();
        border.setCenter(boundary.borderCenterX(), boundary.borderCenterZ());
        border.setSize(boundary.borderSize());
        border.setDamageAmount(0.0);
        border.setDamageBuffer(0.0);
        border.setWarningDistance(0);
        player.setWorldBorder(border);
    }

    private void resetPersonalBorder(Player player) {
        player.setWorldBorder(null);
    }

    private void constrainContestantAfterFailedExit(
            Player player, Contestant contestant) {
        player.setGameMode(GameMode.ADVENTURE);
        applyPersonalBorder(player, contestant);
        buildBossBar.removePlayer(player);
    }

    private void teleport(
            Player player,
            Location destination,
            Runnable onSuccess,
            Runnable onFailure) {
        long attempt = ++nextTeleportAttempt;
        teleportAttempts.put(player.getUniqueId(), attempt);
        expectedTeleportDestinations.put(
                player.getUniqueId(), destination.clone());
        World world =
                Objects.requireNonNull(
                        destination.getWorld(), "teleport destination world");
        String worldKey = world.getKey().toString();
        if (!COMMAND_WORLD_KEY.matcher(worldKey).matches()) {
            logger.severe(
                    "SCENARIOCRAFT_TELEPORT_FAILURE invalid arena world key: "
                            + worldKey);
            player.sendMessage(
                    "The battle could not move you safely. Please ask a grown-up helper.");
            notifyOperatorsOfTeleportFailure(player, destination);
            finishTeleportFailure(player, attempt, onFailure);
            return;
        }
        try {
            String command =
                    "minecraft:execute in "
                            + worldKey
                            + " run minecraft:tp "
                            + player.getUniqueId()
                            + " "
                            + commandNumber(destination.getX())
                            + " "
                            + commandNumber(destination.getY())
                            + " "
                            + commandNumber(destination.getZ())
                            + " "
                            + commandNumber(destination.getYaw())
                            + " "
                            + commandNumber(destination.getPitch());
            if (!server.dispatchCommand(server.getConsoleSender(), command)) {
                scheduleTeleportDispatchRetry(
                        player,
                        destination,
                        command,
                        attempt,
                        onSuccess,
                        onFailure);
                return;
            }
            if (playerReached(player, destination)) {
                finishTeleportSuccess(player, attempt, onSuccess);
                return;
            }
            scheduleTeleportConfirmation(
                    player,
                    destination,
                    attempt,
                    onSuccess,
                    onFailure,
                    0);
        } catch (RuntimeException failure) {
            reportTeleportFailure(
                    player,
                    destination,
                    "console dispatch or verification scheduling failed",
                    failure,
                    attempt,
                    onFailure);
        }
    }

    private void scheduleTeleportDispatchRetry(
            Player player,
            Location destination,
            String command,
            long attempt,
            Runnable onSuccess,
            Runnable onFailure) {
        try {
            server.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!isCurrentTeleportAttempt(player, attempt)) {
                                    return;
                                }
                                if (closed || !player.isOnline()) {
                                    reportTeleportFailure(
                                            player,
                                            destination,
                                            "player unavailable before teleport dispatch retry",
                                            null,
                                            attempt,
                                            onFailure);
                                    return;
                                }
                                if (playerReached(player, destination)) {
                                    finishTeleportSuccess(
                                            player, attempt, onSuccess);
                                    return;
                                }
                                try {
                                    if (!server.dispatchCommand(
                                            server.getConsoleSender(), command)) {
                                        reportTeleportFailure(
                                                player,
                                                destination,
                                                "console command was rejected twice",
                                                null,
                                                attempt,
                                                onFailure);
                                        return;
                                    }
                                    if (playerReached(player, destination)) {
                                        finishTeleportSuccess(
                                                player, attempt, onSuccess);
                                        return;
                                    }
                                    scheduleTeleportConfirmation(
                                            player,
                                            destination,
                                            attempt,
                                            onSuccess,
                                            onFailure,
                                            0);
                                } catch (RuntimeException failure) {
                                    reportTeleportFailure(
                                            player,
                                            destination,
                                            "console dispatch retry failed",
                                            failure,
                                            attempt,
                                            onFailure);
                                }
                            },
                            1L);
        } catch (RuntimeException failure) {
            reportTeleportFailure(
                    player,
                    destination,
                    "teleport dispatch retry scheduling failed",
                    failure,
                    attempt,
                    onFailure);
        }
    }

    private void scheduleTeleportConfirmation(
            Player player,
            Location destination,
            long attempt,
            Runnable onSuccess,
            Runnable onFailure,
            int delayIndex) {
        try {
            server.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!isCurrentTeleportAttempt(player, attempt)) {
                                    return;
                                }
                                if (closed) {
                                    teleportAttempts.remove(
                                            player.getUniqueId(), attempt);
                                    expectedTeleportDestinations.remove(
                                            player.getUniqueId());
                                    strandedArenaPlayers.remove(player.getUniqueId());
                                    return;
                                }
                                if (!player.isOnline()) {
                                    reportTeleportFailure(
                                            player,
                                            destination,
                                            "player disconnected before teleport confirmation",
                                            null,
                                            attempt,
                                            onFailure);
                                    return;
                                }
                                if (playerReached(player, destination)) {
                                    finishTeleportSuccess(
                                            player, attempt, onSuccess);
                                    return;
                                }
                                int nextDelayIndex = delayIndex + 1;
                                if (nextDelayIndex
                                        < TELEPORT_CONFIRMATION_DELAYS.length) {
                                    scheduleTeleportConfirmation(
                                            player,
                                            destination,
                                            attempt,
                                            onSuccess,
                                            onFailure,
                                            nextDelayIndex);
                                    return;
                                }
                                reportTeleportFailure(
                                        player,
                                        destination,
                                        "console command did not move the player after 20 ticks",
                                        null,
                                        attempt,
                                        onFailure);
                            },
                            TELEPORT_CONFIRMATION_DELAYS[delayIndex]);
        } catch (RuntimeException failure) {
            reportTeleportFailure(
                    player,
                    destination,
                    "teleport verification scheduling failed",
                    failure,
                    attempt,
                    onFailure);
        }
    }

    private boolean isCurrentTeleportAttempt(Player player, long attempt) {
        return Objects.equals(
                teleportAttempts.get(player.getUniqueId()), attempt);
    }

    private void finishTeleportSuccess(
            Player player, long attempt, Runnable onSuccess) {
        if (!teleportAttempts.remove(player.getUniqueId(), attempt)) {
            return;
        }
        expectedTeleportDestinations.remove(player.getUniqueId());
        strandedArenaPlayers.remove(player.getUniqueId());
        clearTeleportRecovery(player);
        onSuccess.run();
    }

    private void finishTeleportFailure(
            Player player, long attempt, Runnable onFailure) {
        if (teleportAttempts.remove(player.getUniqueId(), attempt)) {
            expectedTeleportDestinations.remove(player.getUniqueId());
            onFailure.run();
        }
    }

    private void reportTeleportFailure(
            Player player,
            Location destination,
            String reason,
            RuntimeException failure,
            long attempt,
            Runnable onFailure) {
        if (!isCurrentTeleportAttempt(player, attempt)) {
            return;
        }
        String message =
                "SCENARIOCRAFT_TELEPORT_FAILURE "
                        + reason
                        + " for "
                        + player.getName()
                        + " in "
                        + Objects.requireNonNull(
                                        destination.getWorld(),
                                        "teleport destination world")
                                .getKey();
        if (failure == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, failure);
        }
        player.sendMessage(
                "The battle could not move you safely. Please ask a grown-up helper.");
        notifyOperatorsOfTeleportFailure(player, destination);
        finishTeleportFailure(player, attempt, onFailure);
    }

    private void notifyOperatorsOfTeleportFailure(
            Player affectedPlayer, Location destination) {
        World destinationWorld =
                Objects.requireNonNull(
                        destination.getWorld(), "teleport destination world");
        String alert =
                "ScenarioCraft teleport alert: "
                        + affectedPlayer.getName()
                        + " could not move to "
                        + destinationWorld.getKey()
                        + ". Run /battle stop and check SCENARIOCRAFT_TELEPORT_FAILURE.";
        for (Player onlinePlayer : server.getOnlinePlayers()) {
            if (onlinePlayer.isOp()) {
                onlinePlayer.sendMessage(alert);
            }
        }
    }

    private void notifyJoiningOperatorOfPendingTeleportFailures(Player player) {
        if (player.isOp() && !strandedArenaPlayers.isEmpty()) {
            player.sendMessage(
                    "ScenarioCraft recovery alert: "
                            + strandedArenaPlayers.size()
                            + " player move(s) still need a confirmed hub return. Check SCENARIOCRAFT_TELEPORT_FAILURE.");
        }
    }

    private void retryStrandedExit(Player player) {
        GameMode recoveredGameMode = player.getGameMode();
        strandedArenaPlayers.add(player.getUniqueId());
        persistTeleportRecovery(player);
        resetPersonalBorder(player);
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                hubLocation(),
                () -> {
                    player.setGameMode(recoveredGameMode);
                    strandedArenaPlayers.remove(player.getUniqueId());
                    logger.info(
                            "Confirmed recovered hub return for "
                                    + player.getName()
                                    + ".");
                },
                () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                    strandedArenaPlayers.add(player.getUniqueId());
                });
    }

    private static boolean playerReached(Player player, Location destination) {
        Location actual = player.getLocation();
        return actual.getWorld() == destination.getWorld()
                && Math.abs(actual.getX() - destination.getX())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(actual.getY() - destination.getY())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(actual.getZ() - destination.getZ())
                        <= TELEPORT_CONFIRMATION_EPSILON;
    }

    private static String commandNumber(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "teleport coordinates must be finite");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String commandNumber(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    "teleport rotation must be finite");
        }
        return new BigDecimal(Float.toString(value))
                .stripTrailingZeros()
                .toPlainString();
    }

    private Location tourLocation() {
        return hubLocation();
    }

    private InventorySnapshot snapshotPlayerInventory(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        return new InventorySnapshot(
                player.getGameMode(),
                snapshotContents(player.getInventory()),
                snapshotContents(player.getEnderChest()),
                cursor == null || cursor.isEmpty() ? null : cursor.clone());
    }

    private void persistInventorySnapshot(Player player, InventorySnapshot snapshot) {
        player.getPersistentDataContainer()
                .set(
                        inventorySnapshotKey,
                        PersistentDataType.BYTE_ARRAY,
                        encodeInventorySnapshot(snapshot));
        player.saveData();
    }

    private boolean hasPendingTeleportRecovery(Player player) {
        return player.getPersistentDataContainer()
                .has(teleportRecoveryKey, PersistentDataType.BYTE);
    }

    private void persistTeleportRecovery(Player player) {
        if (!hasPendingTeleportRecovery(player)) {
            player.getPersistentDataContainer()
                    .set(
                            teleportRecoveryKey,
                            PersistentDataType.BYTE,
                            (byte) 1);
        }
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "Could not persist teleport recovery marker for "
                            + player.getName(),
                    failure);
            String alert =
                    "ScenarioCraft recovery persistence alert: "
                            + player.getName()
                            + "'s recovery marker could not be saved. Keep the server running, retry their hub return, and check the server log.";
            for (Player onlinePlayer : server.getOnlinePlayers()) {
                if (onlinePlayer.isOp()) {
                    onlinePlayer.sendMessage(alert);
                }
            }
        }
    }

    private void clearTeleportRecovery(Player player) {
        if (!hasPendingTeleportRecovery(player)) {
            return;
        }
        player.getPersistentDataContainer().remove(teleportRecoveryKey);
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Could not persist cleared teleport recovery marker for "
                            + player.getName(),
                    failure);
        }
    }

    private void restorePendingInventory(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        byte[] encoded =
                data.get(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
        if (encoded == null) {
            return;
        }
        resetPersonalBorder(player);
        strandedArenaPlayers.add(player.getUniqueId());
        persistTeleportRecovery(player);
        boolean inventoryRestored = false;
        try {
            restoreInventorySnapshot(player, decodeInventorySnapshot(encoded));
            inventoryRestored = true;
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "Could not restore pending round inventory for " + player.getName(),
                    failure);
            player.sendMessage(
                    "Your saved items need a grown-up helper before the next battle.");
            player.setGameMode(GameMode.ADVENTURE);
        }
        boolean restored = inventoryRestored;
        teleport(
                player,
                hubLocation(),
                () -> {
                    if (restored) {
                        logger.info(
                                "Restored pending round inventory for "
                                        + player.getName()
                                        + ".");
                    }
                },
                () -> {
                    strandedArenaPlayers.add(player.getUniqueId());
                    player.setGameMode(GameMode.ADVENTURE);
                });
    }

    private void restoreInventorySnapshot(Player player, InventorySnapshot snapshot) {
        ItemStack[] inventory = cloneContents(snapshot.inventoryContents());
        ItemStack[] enderChest = cloneContents(snapshot.enderChestContents());
        ItemStack cursor =
                snapshot.cursorItem() == null ? null : snapshot.cursorItem().clone();
        player.closeInventory();
        player.getInventory().setContents(inventory);
        player.getEnderChest().setContents(enderChest);
        player.setItemOnCursor(cursor);
        player.setGameMode(snapshot.originalGameMode());
        player.getPersistentDataContainer().remove(inventorySnapshotKey);
        player.updateInventory();
        player.saveData();
    }

    private byte[] encodeInventorySnapshot(InventorySnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(INVENTORY_SNAPSHOT_VERSION);
                output.writeUTF(snapshot.originalGameMode().name());
                writeItemArray(output, snapshot.inventoryContents());
                writeItemArray(output, snapshot.enderChestContents());
                writeItem(output, snapshot.cursorItem());
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not encode round inventory snapshot", failure);
        }
    }

    private InventorySnapshot decodeInventorySnapshot(byte[] encoded) {
        try (DataInputStream input =
                new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = input.readInt();
            if (version != INVENTORY_SNAPSHOT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported round inventory snapshot version " + version);
            }
            GameMode gameMode = GameMode.valueOf(input.readUTF());
            ItemStack[] inventory = readItemArray(input);
            ItemStack[] enderChest = readItemArray(input);
            ItemStack cursor = readItem(input);
            if (input.available() != 0) {
                throw new IllegalArgumentException("Invalid round inventory snapshot");
            }
            return new InventorySnapshot(gameMode, inventory, enderChest, cursor);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not decode round inventory snapshot", failure);
        }
    }

    private static void writeItemArray(DataOutputStream output, ItemStack[] items)
            throws IOException {
        output.writeInt(items.length);
        for (ItemStack item : items) {
            writeItem(output, item);
        }
    }

    private static ItemStack[] readItemArray(DataInputStream input) throws IOException {
        int slots = input.readInt();
        if (slots < 0 || slots > MAX_SNAPSHOT_SLOTS) {
            throw new IllegalArgumentException("Invalid round inventory snapshot slot count");
        }
        ItemStack[] items = new ItemStack[slots];
        for (int slot = 0; slot < slots; slot++) {
            items[slot] = readItem(input);
        }
        return items;
    }

    private static void writeItem(DataOutputStream output, ItemStack item)
            throws IOException {
        boolean present = item != null && !item.isEmpty();
        output.writeBoolean(present);
        if (!present) {
            return;
        }
        byte[] encoded = item.serializeAsBytes();
        if (encoded.length > MAX_SNAPSHOT_SECTION_BYTES) {
            throw new IllegalArgumentException("Round inventory item is too large to save");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static ItemStack readItem(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return null;
        }
        int length = input.readInt();
        if (length < 0 || length > MAX_SNAPSHOT_SECTION_BYTES) {
            throw new IllegalArgumentException("Invalid round inventory snapshot item");
        }
        byte[] item = input.readNBytes(length);
        if (item.length != length) {
            throw new IllegalArgumentException("Truncated round inventory snapshot item");
        }
        return ItemStack.deserializeBytes(item);
    }

    private static ItemStack[] snapshotContents(Inventory inventory) {
        return cloneContents(inventory.getContents());
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            snapshot[slot] = item == null || item.isEmpty() ? null : item.clone();
        }
        return snapshot;
    }

    private static void placeTaskBook(ArenaWorld arena) {
        SecretChestPosition chestPosition = SecretChestPosition.atHub(arena);
        Block block =
                arena.world()
                        .getBlockAt(
                                chestPosition.x(),
                                chestPosition.y(),
                                chestPosition.z());
        if (!(block.getState() instanceof Chest chest)) {
            throw new IllegalStateException("secret-note block is not a chest");
        }

        ItemStack writtenBook = new ItemStack(Material.WRITTEN_BOOK);
        if (!(writtenBook.getItemMeta() instanceof BookMeta bookMeta)) {
            throw new IllegalStateException("written book metadata is unavailable");
        }
        bookMeta.setTitle("Secret Build Idea");
        bookMeta.setAuthor("ScenarioCraft");
        bookMeta.setPages(
                "The secret build idea appears in chat and a title when the picker opens this chest!");
        writtenBook.setItemMeta(bookMeta);

        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        inventory.setItem(inventory.getSize() / 2, writtenBook);
    }

    private static String formatTime(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String friendlyPhase(RoundPhase phase) {
        return phase.name().toLowerCase().replace('_', ' ');
    }

    private record Contestant(
            UUID playerId,
            PlotBounds plot,
            PlotBoundary boundary,
            InventorySnapshot inventorySnapshot) {}

    private record InventorySnapshot(
            GameMode originalGameMode,
            ItemStack[] inventoryContents,
            ItemStack[] enderChestContents,
            ItemStack cursorItem) {}

    private record Spectator(
            UUID playerId, Location originalLocation, GameMode originalGameMode) {}

    @FunctionalInterface
    private interface OnlineContestantAction {
        void accept(Player player, Contestant contestant);
    }
}
