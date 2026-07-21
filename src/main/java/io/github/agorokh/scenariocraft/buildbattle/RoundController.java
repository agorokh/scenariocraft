package io.github.agorokh.scenariocraft.buildbattle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
    private static final String ALERT_PERMISSION = "scenariocraft.alerts";
    private static final int MAX_SNAPSHOT_SECTION_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SNAPSHOT_SLOTS = 128;
    private static final double TELEPORT_CONFIRMATION_EPSILON = 1.0E-6;
    private static final long[] TELEPORT_CONFIRMATION_DELAYS = {1L, 4L, 15L};

    private final Plugin plugin;
    private final Server server;
    private final BattleSettings settings;
    private final ArenaWorld arena;
    private final BatchedBlockEditor blockEditor;
    private final Logger logger;
    private final PickerSelector pickerSelector;
    private final TaskDeck taskDeck;
    private final Consumer<String> taskBookPlacer;
    private final RoundExporter roundExporter;
    private final TeleportTransport teleportTransport;
    private final TeleportRecoveryStore recoveryStore;
    private final ActiveArenaMutationPolicy mutationPolicy;
    private final ActiveArenaMutationListener mutationListener;
    private final RoundStateMachine state = new RoundStateMachine();
    private final Map<UUID, Contestant> contestants = new LinkedHashMap<>();
    private final Map<UUID, Spectator> revealSpectators = new LinkedHashMap<>();
    private final Set<UUID> strandedArenaPlayers = new LinkedHashSet<>();
    private final Map<UUID, GameMode> recoveryGameModes = new LinkedHashMap<>();
    private final Map<UUID, TeleportAttempt> teleportAttempts = new LinkedHashMap<>();
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
    private String resultRoundId;
    private boolean resultExportStarted;

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
                ignored -> placeTaskBook(arena),
                RoundExportService.forPlugin(
                        plugin,
                        arena.world(),
                        settings.arena().blocksPerTick(),
                        logger),
                new TeleportTransport(plugin.getServer()),
                recoveryStoreFor(plugin));
    }

    public RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger,
            TeleportTransport teleportTransport,
            TeleportRecoveryStore recoveryStore) {
        this(
                plugin,
                settings,
                arena,
                blockEditor,
                logger,
                bound -> ThreadLocalRandom.current().nextInt(bound),
                ignored -> placeTaskBook(arena),
                RoundExportService.forPlugin(
                        plugin,
                        arena.world(),
                        settings.arena().blocksPerTick(),
                        logger),
                teleportTransport,
                recoveryStore);
    }

    RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger,
            IntUnaryOperator randomIndex,
            Consumer<String> taskBookPlacer) {
        this(
                plugin,
                settings,
                arena,
                blockEditor,
                logger,
                randomIndex,
                taskBookPlacer,
                ignored -> {},
                new TeleportTransport(plugin.getServer()),
                TeleportRecoveryStore.inMemory());
    }

    RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger,
            IntUnaryOperator randomIndex,
            Consumer<String> taskBookPlacer,
            RoundExporter roundExporter) {
        this(
                plugin,
                settings,
                arena,
                blockEditor,
                logger,
                randomIndex,
                taskBookPlacer,
                roundExporter,
                new TeleportTransport(plugin.getServer()),
                TeleportRecoveryStore.inMemory());
    }

    RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger,
            IntUnaryOperator randomIndex,
            Consumer<String> taskBookPlacer,
            RoundExporter roundExporter,
            TeleportTransport teleportTransport,
            TeleportRecoveryStore recoveryStore) {
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
        this.roundExporter = Objects.requireNonNull(roundExporter, "roundExporter");
        this.teleportTransport = Objects.requireNonNull(teleportTransport, "teleportTransport");
        this.recoveryStore = Objects.requireNonNull(recoveryStore, "recoveryStore");
        this.inventorySnapshotKey =
                new NamespacedKey(plugin, "round-inventory-snapshot");
        this.teleportRecoveryKey =
                new NamespacedKey(plugin, "teleport-recovery-pending");
        this.buildBossBar =
                server.createBossBar(
                        "Build time", BarColor.BLUE, BarStyle.SOLID);
        buildBossBar.setVisible(false);
        this.mutationPolicy =
                new ActiveArenaMutationPolicy(
                        arena.world(),
                        this::phase,
                        this::isArenaProtected,
                        playerId -> {
                            Contestant contestant = contestants.get(playerId);
                            return contestant == null ? null : contestant.boundary();
                        },
                        () ->
                                contestants.values().stream()
                                        .map(Contestant::boundary)
                                        .toList(),
                        strandedArenaPlayers::contains);
        this.mutationListener = new ActiveArenaMutationListener(mutationPolicy);
        server.getPluginManager().registerEvents(this, plugin);
        server.getPluginManager().registerEvents(mutationListener, plugin);
        this.timerTask =
                server.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        strandedArenaPlayers.addAll(recoveryStore.pendingPlayers());
        if (!strandedArenaPlayers.isEmpty()) {
            logger.warning(
                    "SCENARIOCRAFT_PENDING_RECOVERY_COUNT "
                            + strandedArenaPlayers.size()
                            + " player(s) await a confirmed hub return from "
                            + recoveryStore.location()
                            + "; recovery resumes when each player rejoins.");
        }
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

    private static TeleportRecoveryStore recoveryStoreFor(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) {
            return TeleportRecoveryStore.inMemory();
        }
        return TeleportRecoveryStore.open(
                dataFolder.toPath().resolve("pending-teleport-recovery.txt"));
    }

    @Override
    public RoundPhase phase() {
        return state.phase();
    }

    public Optional<Location> winnerCelebrationLocation(String playerName) {
        Objects.requireNonNull(playerName, "playerName");
        if (phase() != RoundPhase.REVEAL) {
            return Optional.empty();
        }
        return contestants.values().stream()
                .filter(contestant -> contestant.playerName().equalsIgnoreCase(playerName))
                .findFirst()
                .map(
                        contestant ->
                                new Location(
                                        arena.world(),
                                        contestant.plot().centerX() + 0.5,
                                        arena.floorY() + 3.0,
                                        contestant.plot().centerZ() + 0.5));
    }

    public Optional<String> resultRoundId() {
        if (phase() != RoundPhase.REVEAL) {
            return Optional.empty();
        }
        if (!resultExportStarted) {
            return Optional.empty();
        }
        if (resultRoundId == null) {
            resultRoundId = roundExporter.currentRoundId().orElse(null);
        }
        return Optional.ofNullable(resultRoundId);
    }

    /** Returns the current plot center used for a short winner-particle celebration. */
    public Location resultCelebrationLocation(String plotId) {
        if (plotId == null || !plotId.matches("p[1-9][0-9]*")) {
            return null;
        }
        int plotNumber;
        try {
            plotNumber = Integer.parseInt(plotId.substring(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (plotNumber > plots.size()) {
            return null;
        }
        PlotBounds plot = plots.get(plotNumber - 1);
        return new Location(
                arena.world(),
                plot.centerX() + 0.5,
                arena.floorY() + 3.0,
                plot.centerZ() + 0.5);
    }

    @Override
    public Optional<String> activeResultRoundId() {
        return resultRoundId();
    }

    ActiveArenaMutationListener mutationListener() {
        return mutationListener;
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
        if (roundExporter.isBusy()) {
            sender.sendMessage(
                    "The last build is still being packed up safely — just a moment!");
            return;
        }
        List<Player> players =
                server.getOnlinePlayers().stream()
                        .map(Player.class::cast)
                        .filter(player -> !settings.isExempt(player.getName()))
                        .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        List<Player> pendingRecoveryPlayers =
                players.stream().filter(this::hasPendingTeleportRecovery).toList();
        if (!pendingRecoveryPlayers.isEmpty()) {
            for (Player player : pendingRecoveryPlayers) {
                retryStrandedExit(player);
            }
            if (players.stream().anyMatch(this::hasPendingTeleportRecovery)) {
                sender.sendMessage(
                        "A builder is still returning safely to the hub. Please wait for a grown-up helper.");
                return;
            }
        }
        if (!teleportTransport.isAvailable()) {
            reportTransportUnavailable(
                    sender,
                    "round start requires minecraft:execute and minecraft:tp");
            return;
        }
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
        resultRoundId = null;
        resultExportStarted = false;
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
                                player.getName(),
                                plots.get(index),
                                boundaries.get(index),
                                inventorySnapshot));
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
        boolean hadPendingInventory =
                player.getPersistentDataContainer()
                        .has(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
        if (hasPendingTeleportRecovery(player)) {
            if (hadPendingInventory) {
                restorePendingInventory(
                        player, () -> resumeActiveContestant(player));
            } else {
                retryStrandedExit(
                        player, 0L, () -> resumeActiveContestant(player));
            }
            return;
        }
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
        restorePendingInventory(player);
        if (hadPendingInventory) {
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
        if (abandonedPlotEntry) {
            finishBeginBuildingIfReady();
        }
        Contestant contestant = contestants.get(player.getUniqueId());
        if (contestant != null) {
            restoreContestantToHub(player, contestant);
        }
        Spectator spectator = revealSpectators.get(player.getUniqueId());
        if (spectator != null) {
            restoreSpectator(player, spectator);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Contestant contestant = contestants.get(player.getUniqueId());
        if (phase() != RoundPhase.BUILDING || contestant == null) {
            return;
        }

        player.setGameMode(GameMode.ADVENTURE);
        buildBossBar.removePlayer(player);
        if (hasPendingTeleportRecovery(player)) {
            event.setRespawnLocation(hubLocation());
            retryStrandedExit(
                    player, 1L, () -> resumeActiveContestant(player));
            return;
        }
        if (!isUsablePlot(contestant)) {
            Location hub = hubLocation();
            event.setRespawnLocation(hub);
            markStrandedForRecovery(player);
            recoveryGameModes.putIfAbsent(
                    player.getUniqueId(), contestant.inventorySnapshot().originalGameMode());
            reportRelocationFailure(
                    player,
                    hub,
                    "assigned plot was unavailable during BUILDING respawn",
                    null);
            teleportAfter(
                    player,
                    hub,
                    () ->
                            finishHubRecovery(
                                    player,
                                    () -> resetPersonalBorder(player)),
                    () -> markStrandedForRecovery(player),
                    1L);
            return;
        }

        Location destination = plotLocation(contestant);
        event.setRespawnLocation(destination);
        teleportAfter(
                player,
                destination,
                () -> {
                    if (phase() == RoundPhase.BUILDING
                            && contestants.get(player.getUniqueId()) == contestant) {
                        applyPersonalBorder(player, contestant);
                        enableBuildingControls(player);
                    } else {
                        player.setGameMode(GameMode.ADVENTURE);
                        markStrandedForRecovery(player);
                    }
                },
                () -> constrainContestantAfterFailedExit(player, contestant),
                1L);
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
    public void onContestantTeleport(PlayerTeleportEvent event) {
        Contestant contestant = contestants.get(event.getPlayer().getUniqueId());
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        if (hasPendingTeleportRecovery(event.getPlayer())) {
            if (matchesExpectedTeleport(event.getPlayer(), destination)) {
                return;
            }
            if (!sameDestination(hubLocation(), destination)) {
                event.setCancelled(true);
                return;
            }
            retryStrandedExit(
                    event.getPlayer(), 1L, () -> resumeActiveContestant(event.getPlayer()));
            return;
        }
        boolean plotContainmentActive =
                phase() == RoundPhase.BUILDING
                        || (phase() == RoundPhase.NOTE_PICK
                                && awaitingPlotEntries);
        if (!plotContainmentActive) {
            return;
        }
        if (contestant == null) {
            if (isInsidePrivatePlot(destination)) {
                event.setCancelled(true);
            }
            return;
        }
        if (matchesExpectedTeleport(event.getPlayer(), destination)) {
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        settleTeleportAttemptsForClose(false);
        timerTask.cancel();
        blockEditor.cancel();
        timer = null;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        try {
            roundExporter.close();
        } catch (Exception failure) {
            logger.log(Level.WARNING, "Could not close the round exporter cleanly", failure);
        }
        restoreRoundPlayers();
        settleTeleportAttemptsForClose(true);
        contestants.clear();
        revealSpectators.clear();
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
        forEachOnlineContestant(
                (player, ignored) -> enableBuildingControls(player));
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
        exportRound();
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

    private void exportRound() {
        if (currentTask == null) {
            logger.severe("SCENARIOCRAFT_EXPORT_FAILURE no task is available at REVEAL");
            return;
        }
        int originY = Math.addExact(arena.floorY(), 1);
        int plotHeight = settings.arena().wallHeight();
        List<RoundExportRequest.Plot> exportPlots = new java.util.ArrayList<>();
        int plotNumber = 1;
        for (Contestant contestant : contestants.values()) {
            PlotBounds plot = contestant.plot();
            exportPlots.add(
                    new RoundExportRequest.Plot(
                            "p" + plotNumber,
                            contestant.playerName(),
                            plot.minX(),
                            originY,
                            plot.minZ(),
                            plot.width(),
                            plotHeight,
                            plot.depth()));
            plotNumber++;
        }
        try {
            roundExporter.export(
                    new RoundExportRequest(
                            currentTask, arena.world().getName(), exportPlots));
            resultExportStarted = true;
            resultExportStarted = true;
        } catch (RuntimeException failure) {
            logger.log(Level.SEVERE, "SCENARIOCRAFT_EXPORT_FAILURE export did not start", failure);
        }
    }

    private void abortRound() {
        roundExporter.cancel();
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
        if (hasPendingTeleportRecovery(player)) {
            retryStrandedExit(
                    player,
                    0L,
                    () -> resumeActiveContestant(player),
                    () -> failPendingPlotEntry(player));
            return;
        }
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
        if (hasPendingTeleportRecovery(player)) {
            retryStrandedExit(
                    player,
                    0L,
                    () -> resumeActiveContestant(player),
                    () -> failPendingPlotEntry(player));
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                hubLocation(),
                () -> {
                    resetPersonalBorder(player);
                    buildBossBar.removePlayer(player);
                },
                () -> {
                    if (phase() == RoundPhase.BUILDING) {
                        constrainContestantAfterFailedExit(player, contestant);
                    } else {
                        recoveryGameModes.putIfAbsent(
                                player.getUniqueId(),
                                contestant.inventorySnapshot().originalGameMode());
                        markStrandedForRecovery(player);
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                    buildBossBar.removePlayer(player);
                });
    }

    private void moveToPlot(Player player, Contestant contestant) {
        if (awaitingPlotEntries) {
            pendingPlotEntries.add(player.getUniqueId());
        }
        if (hasPendingTeleportRecovery(player)) {
            retryStrandedExit(
                    player,
                    0L,
                    () -> resumeActiveContestant(player),
                    () -> failPendingPlotEntry(player));
            return;
        }
        clearRoundInventory(player);
        if (!isInsideAssignedPlot(player, contestant)) {
            resetPersonalBorder(player);
        }
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                plotLocation(contestant),
                () -> {
                    applyPersonalBorder(player, contestant);
                    if (phase() == RoundPhase.BUILDING) {
                        enableBuildingControls(player);
                    }
                    if (pendingPlotEntries.remove(player.getUniqueId())) {
                        finishBeginBuildingIfReady();
                    }
                },
                () -> {
                    buildBossBar.removePlayer(player);
                    pendingPlotEntries.remove(player.getUniqueId());
                    if (phase() == RoundPhase.BUILDING
                            && contestants.get(player.getUniqueId())
                                    == contestant) {
                        constrainContestantAfterFailedExit(player, contestant);
                    } else {
                        markStrandedForRecovery(player);
                        resetPersonalBorder(player);
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                    if (phase() == RoundPhase.NOTE_PICK
                            && awaitingPlotEntries) {
                        plotEntryFailed = true;
                        if (!movingContestantsToPlots) {
                            abortAfterPlotEntryFailure();
                        }
                    }
                });
    }

    private void abortAfterPlotEntryFailure() {
        if (phase() != RoundPhase.NOTE_PICK || !awaitingPlotEntries) {
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
        if (hasPendingTeleportRecovery(player)) {
            retryStrandedExit(
                    player, 0L, () -> resumeActiveContestant(player));
            return;
        }
        clearRoundInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        teleport(
                player,
                tourLocation(),
                () -> resetPersonalBorder(player),
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
        markStrandedForRecovery(player);
        boolean inventoryRestored = false;
        try {
            restoreInventorySnapshot(player, contestant.inventorySnapshot());
            inventoryRestored = true;
            recoveryGameModes.put(
                    player.getUniqueId(), contestant.inventorySnapshot().originalGameMode());
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "Could not restore round inventory for " + player.getName(),
                    failure);
            player.sendMessage(
                    "Your saved items need a grown-up helper before the next battle.");
            player.setGameMode(GameMode.ADVENTURE);
        }
        teleport(
                player,
                hubLocation(),
                () ->
                        finishHubRecovery(
                                player,
                                () -> resetPersonalBorder(player)),
                () -> {
                    markStrandedForRecovery(player);
                    player.setGameMode(GameMode.ADVENTURE);
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
        player.setGameMode(spectator.originalGameMode());
        teleport(
                player,
                spectator.originalLocation(),
                () -> {},
                () -> player.setGameMode(spectator.originalGameMode()));
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

    private boolean isArenaProtected() {
        return phase() != RoundPhase.IDLE || roundExporter.isReadingArena();
    }

    private boolean mayContestantEdit(Player player, Block block) {
        ActiveArenaMutationPolicy.Position position =
                new ActiveArenaMutationPolicy.Position(
                        block.getWorld(), block.getX(), block.getY(), block.getZ());
        return mutationPolicy.allows(
                ActiveArenaMutationPolicy.Family.PLAYER_INTERACTION,
                player.getUniqueId(),
                position,
                List.of(position));
    }

    private boolean isInsidePrivatePlot(Location destination) {
        if (destination.getWorld() != arena.world()) {
            return false;
        }
        int x = destination.getBlockX();
        int z = destination.getBlockZ();
        return contestants.values().stream()
                .map(Contestant::plot)
                .anyMatch(
                        plot ->
                                x >= plot.minX()
                                        && x <= plot.maxX()
                                        && z >= plot.minZ()
                                        && z <= plot.maxZ());
    }

    private boolean matchesExpectedTeleport(
            Player player, Location destination) {
        TeleportAttempt attempt = teleportAttempts.get(player.getUniqueId());
        Location expected = attempt == null ? null : attempt.destination();
        return expected != null && sameDestination(expected, destination);
    }

    private static boolean sameDestination(Location expected, Location actual) {
        return expected.getWorld() == actual.getWorld()
                && Math.abs(expected.getX() - actual.getX())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(expected.getY() - actual.getY())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(expected.getZ() - actual.getZ())
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
        markStrandedForRecovery(player);
        recoveryGameModes.putIfAbsent(
                player.getUniqueId(), contestant.inventorySnapshot().originalGameMode());
        player.setGameMode(GameMode.ADVENTURE);
        if (isInsideAssignedPlot(player, contestant)) {
            applyPersonalBorder(player, contestant);
        } else {
            resetPersonalBorder(player);
        }
        buildBossBar.removePlayer(player);
    }

    private void enableBuildingControls(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        buildBossBar.addPlayer(player);
    }

    private void teleport(
            Player player,
            Location destination,
            Runnable onSuccess,
            Runnable onFailure) {
        beginTeleportAttempt(player, destination, onSuccess, onFailure, 0L);
    }

    private void teleportAfter(
            Player player,
            Location destination,
            Runnable onSuccess,
            Runnable onFailure,
            long delayTicks) {
        beginTeleportAttempt(player, destination, onSuccess, onFailure, delayTicks);
    }

    private void beginTeleportAttempt(
            Player player,
            Location destination,
            Runnable onSuccess,
            Runnable onFailure,
            long delayTicks) {
        Objects.requireNonNull(destination.getWorld(), "teleport destination world");
        TeleportAttempt previous = teleportAttempts.remove(player.getUniqueId());
        if (previous != null) {
            cancelAttempt(previous);
        }
        TeleportAttempt attempt =
                new TeleportAttempt(
                        player,
                        destination.clone(),
                        onSuccess,
                        onFailure);
        teleportAttempts.put(player.getUniqueId(), attempt);
        if (delayTicks > 0L) {
            scheduleOwnedTask(
                    attempt,
                    () -> dispatchTeleport(attempt, false),
                    delayTicks,
                    "teleport dispatch scheduling failed");
        } else {
            dispatchTeleport(attempt, false);
        }
    }

    private void dispatchTeleport(TeleportAttempt attempt, boolean retry) {
        if (!isCurrentTeleportAttempt(attempt)) {
            return;
        }
        Player player = attempt.player();
        Location destination = attempt.destination();
        if (!player.isOnline()) {
            reportTeleportFailure(
                    attempt,
                    "player disconnected before teleport dispatch",
                    null);
            return;
        }
        if (playerReached(player, destination)) {
            finishTeleportSuccess(attempt);
            return;
        }
        if (!teleportTransport.isAvailable()) {
            reportTeleportFailure(
                    attempt,
                    "namespaced console transport is unavailable",
                    null);
            return;
        }
        try {
            if (!teleportTransport.dispatch(player, destination)) {
                if (retry) {
                    reportTeleportFailure(
                            attempt,
                            "console command was rejected twice",
                            null);
                } else {
                    scheduleOwnedTask(
                            attempt,
                            () -> dispatchTeleport(attempt, true),
                            1L,
                            "teleport dispatch retry scheduling failed");
                }
                return;
            }
            if (playerReached(player, destination)) {
                finishTeleportSuccess(attempt);
                return;
            }
            scheduleTeleportConfirmation(attempt, 0);
        } catch (RuntimeException failure) {
            reportTeleportFailure(
                    attempt,
                    retry
                            ? "console dispatch retry failed"
                            : "console dispatch or verification scheduling failed",
                    failure);
        }
    }

    private void scheduleTeleportConfirmation(TeleportAttempt attempt, int delayIndex) {
        scheduleOwnedTask(
                attempt,
                () -> {
                    if (!isCurrentTeleportAttempt(attempt)) {
                        return;
                    }
                    Player player = attempt.player();
                    if (playerReached(player, attempt.destination())) {
                        finishTeleportSuccess(attempt);
                        return;
                    }
                    if (closed || !player.isOnline()) {
                        reportTeleportFailure(
                                attempt,
                                closed
                                        ? "controller closed before teleport confirmation"
                                        : "player disconnected before teleport confirmation",
                                null);
                        return;
                    }
                    int nextDelayIndex = delayIndex + 1;
                    if (nextDelayIndex < TELEPORT_CONFIRMATION_DELAYS.length) {
                        scheduleTeleportConfirmation(attempt, nextDelayIndex);
                        return;
                    }
                    reportTeleportFailure(
                            attempt,
                            "console command did not move the player after 20 ticks",
                            null);
                },
                TELEPORT_CONFIRMATION_DELAYS[delayIndex],
                "teleport verification scheduling failed");
    }

    private void scheduleOwnedTask(
            TeleportAttempt attempt,
            Runnable action,
            long delayTicks,
            String schedulingFailureReason) {
        try {
            BukkitTask task =
                    server.getScheduler().runTaskLater(plugin, action, delayTicks);
            if (isCurrentTeleportAttempt(attempt)) {
                attempt.tasks().add(task);
            } else {
                task.cancel();
            }
        } catch (RuntimeException failure) {
            reportTeleportFailure(attempt, schedulingFailureReason, failure);
        }
    }

    private boolean isCurrentTeleportAttempt(TeleportAttempt attempt) {
        return !attempt.completed()
                && teleportAttempts.get(attempt.player().getUniqueId()) == attempt;
    }

    private void finishTeleportSuccess(TeleportAttempt attempt) {
        if (!completeAttempt(attempt)) {
            return;
        }
        attempt.onSuccess().run();
    }

    private void finishTeleportFailure(TeleportAttempt attempt) {
        if (completeAttempt(attempt)) {
            attempt.onFailure().run();
        }
    }

    private void reportTeleportFailure(
            TeleportAttempt attempt,
            String reason,
            RuntimeException failure) {
        if (!isCurrentTeleportAttempt(attempt)) {
            return;
        }
        reportRelocationFailure(
                attempt.player(), attempt.destination(), reason, failure);
        finishTeleportFailure(attempt);
    }

    private void reportRelocationFailure(
            Player player,
            Location destination,
            String reason,
            RuntimeException failure) {
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
    }

    private boolean completeAttempt(TeleportAttempt attempt) {
        if (!isCurrentTeleportAttempt(attempt)) {
            return false;
        }
        teleportAttempts.remove(attempt.player().getUniqueId());
        attempt.complete();
        attempt.tasks().forEach(BukkitTask::cancel);
        attempt.tasks().clear();
        return true;
    }

    private void cancelAttempt(TeleportAttempt attempt) {
        attempt.complete();
        attempt.tasks().forEach(BukkitTask::cancel);
        attempt.tasks().clear();
    }

    private void settleTeleportAttemptsForClose(boolean reportUnconfirmedRecovery) {
        for (TeleportAttempt attempt : List.copyOf(teleportAttempts.values())) {
            if (playerReached(attempt.player(), attempt.destination())) {
                if (hasPendingTeleportRecovery(attempt.player())
                        && playerReached(attempt.player(), hubLocation())) {
                    finishTeleportSuccess(attempt);
                } else {
                    completeAttempt(attempt);
                }
            } else {
                if (reportUnconfirmedRecovery
                        && hasPendingTeleportRecovery(attempt.player())
                        && locationsMatch(attempt.destination(), hubLocation())) {
                    reportRelocationFailure(
                            attempt.player(),
                            attempt.destination(),
                            "controller closed before hub recovery confirmation; durable recovery remains pending",
                            null);
                }
                completeAttempt(attempt);
            }
        }
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
        server.getConsoleSender().sendMessage(alert);
        for (Player onlinePlayer : server.getOnlinePlayers()) {
            if (receivesOperatorAlerts(onlinePlayer)) {
                onlinePlayer.sendMessage(alert);
            }
        }
    }

    private void notifyJoiningOperatorOfPendingTeleportFailures(Player player) {
        boolean markerOnlyFailure =
                hasPendingTeleportRecovery(player)
                        && !strandedArenaPlayers.contains(player.getUniqueId());
        int pendingCount =
                strandedArenaPlayers.size() + (markerOnlyFailure ? 1 : 0);
        if (receivesOperatorAlerts(player) && pendingCount > 0) {
            player.sendMessage(
                    "ScenarioCraft recovery alert: "
                            + pendingCount
                            + " player move(s) still need a confirmed hub return. Check SCENARIOCRAFT_TELEPORT_FAILURE.");
        }
    }

    private static boolean receivesOperatorAlerts(Player player) {
        return player.isOp() || player.hasPermission(ALERT_PERMISSION);
    }

    private void retryStrandedExit(Player player) {
        retryStrandedExit(player, 0L, () -> {}, () -> {});
    }

    private void retryStrandedExit(
            Player player, long delayTicks, Runnable afterRecovery) {
        retryStrandedExit(player, delayTicks, afterRecovery, () -> {});
    }

    private void retryStrandedExit(
            Player player,
            long delayTicks,
            Runnable afterRecovery,
            Runnable afterFailure) {
        GameMode recoveredGameMode =
                recoveryGameModes.getOrDefault(
                        player.getUniqueId(), player.getGameMode());
        strandedArenaPlayers.add(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        Location destination = hubLocation();
        if (playerReached(player, destination)) {
            finishHubRecovery(
                    player,
                    () -> {
                        resetPersonalBorder(player);
                        player.setGameMode(recoveredGameMode);
                        logger.info(
                                "Cleared recovered hub marker for "
                                        + player.getName()
                                        + " after confirming they were already safe.");
                        afterRecovery.run();
                    });
            return;
        }
        persistTeleportRecovery(player);
        Runnable onSuccess =
                () ->
                        finishHubRecovery(
                                player,
                                () -> {
                                    resetPersonalBorder(player);
                                    player.setGameMode(recoveredGameMode);
                                    logger.info(
                                            "Confirmed recovered hub return for "
                                                    + player.getName()
                                                    + ".");
                                    afterRecovery.run();
                                });
        Runnable onFailure =
                () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                    markStrandedForRecovery(player);
                    afterFailure.run();
                };
        if (delayTicks > 0L) {
            teleportAfter(player, destination, onSuccess, onFailure, delayTicks);
        } else {
            teleport(player, destination, onSuccess, onFailure);
        }
    }

    private void failPendingPlotEntry(Player player) {
        if (phase() != RoundPhase.NOTE_PICK || !awaitingPlotEntries) {
            return;
        }
        pendingPlotEntries.remove(player.getUniqueId());
        plotEntryFailed = true;
        if (!movingContestantsToPlots) {
            abortAfterPlotEntryFailure();
        }
    }

    private void resumeActiveContestant(Player player) {
        Contestant contestant = contestants.get(player.getUniqueId());
        if (!closed
                && player.isOnline()
                && contestant != null
                && phase() != RoundPhase.IDLE) {
            applyCurrentPhase(player, contestant);
        }
    }

    private static boolean playerReached(Player player, Location destination) {
        return locationsMatch(player.getLocation(), destination);
    }

    private static boolean locationsMatch(Location actual, Location destination) {
        return actual.getWorld() == destination.getWorld()
                && Math.abs(actual.getX() - destination.getX())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(actual.getY() - destination.getY())
                        <= TELEPORT_CONFIRMATION_EPSILON
                && Math.abs(actual.getZ() - destination.getZ())
                        <= TELEPORT_CONFIRMATION_EPSILON;
    }

    private void reportTransportUnavailable(CommandSender sender, String operation) {
        String logMessage =
                "SCENARIOCRAFT_TELEPORT_FAILURE "
                        + operation
                        + "; restore minecraft:execute and minecraft:tp before retrying";
        logger.severe(logMessage);
        String operatorMessage =
                "ScenarioCraft teleport alert: the exact namespaced console transport is unavailable. Restore minecraft:execute and minecraft:tp, then retry.";
        server.getConsoleSender().sendMessage(operatorMessage);
        for (Player onlinePlayer : server.getOnlinePlayers()) {
            if (receivesOperatorAlerts(onlinePlayer)) {
                onlinePlayer.sendMessage(operatorMessage);
            }
        }
        sender.sendMessage(
                sender instanceof Player
                        ? "The battle cannot move everyone safely yet. Please ask a grown-up helper."
                        : operatorMessage);
    }

    private boolean isUsablePlot(Contestant contestant) {
        Location destination = plotLocation(contestant);
        return plots.contains(contestant.plot())
                && destination.getBlockY() >= arena.world().getMinHeight()
                && destination.getBlockY() < arena.world().getMaxHeight()
                && contestant.boundary()
                        .containsEditableBlock(
                                destination.getBlockX(),
                                destination.getBlockY(),
                                destination.getBlockZ());
    }

    private boolean isInsideAssignedPlot(Player player, Contestant contestant) {
        Location actual = player.getLocation();
        return actual.getWorld() == arena.world()
                && contestant.boundary()
                        .containsEditableBlock(
                                actual.getBlockX(), actual.getBlockY(), actual.getBlockZ());
    }

    private Location plotLocation(Contestant contestant) {
        PlotBounds plot = contestant.plot();
        return new Location(
                arena.world(),
                plot.centerX() + 0.5,
                arena.floorY() + 1.0,
                plot.centerZ() + 0.5);
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
        UUID playerId = player.getUniqueId();
        return strandedArenaPlayers.contains(playerId)
                || recoveryStore.contains(playerId)
                || player.getPersistentDataContainer()
                        .has(teleportRecoveryKey, PersistentDataType.BYTE);
    }

    private void persistTeleportRecovery(Player player) {
        try {
            recoveryStore.add(player.getUniqueId());
        } catch (RuntimeException failure) {
            reportRecoveryRegistryFailure(player, "could not be saved", failure);
        }
        if (!player.getPersistentDataContainer()
                .has(teleportRecoveryKey, PersistentDataType.BYTE)) {
            player.getPersistentDataContainer()
                    .set(
                            teleportRecoveryKey,
                            PersistentDataType.BYTE,
                            (byte) 1);
        }
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            reportRecoveryPersistenceFailure(
                    player, "player-data recovery marker could not be saved", failure);
        }
    }

    private void markStrandedForRecovery(Player player) {
        strandedArenaPlayers.add(player.getUniqueId());
        persistTeleportRecovery(player);
    }

    private void finishHubRecovery(Player player, Runnable onCleared) {
        if (!playerReached(player, hubLocation())) {
            markStrandedForRecovery(player);
            return;
        }
        player.getPersistentDataContainer().remove(teleportRecoveryKey);
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            player.getPersistentDataContainer()
                    .set(teleportRecoveryKey, PersistentDataType.BYTE, (byte) 1);
            reportRecoveryPersistenceFailure(
                    player, "cleared player-data recovery marker could not be saved", failure);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        try {
            recoveryStore.remove(player.getUniqueId());
        } catch (RuntimeException failure) {
            player.getPersistentDataContainer()
                    .set(teleportRecoveryKey, PersistentDataType.BYTE, (byte) 1);
            try {
                player.saveData();
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            reportRecoveryRegistryFailure(player, "could not be cleared", failure);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        strandedArenaPlayers.remove(player.getUniqueId());
        recoveryGameModes.remove(player.getUniqueId());
        onCleared.run();
    }

    private void reportRecoveryPersistenceFailure(
            Player player, String reason, RuntimeException failure) {
        logger.log(
                Level.SEVERE,
                "SCENARIOCRAFT_RECOVERY_PERSISTENCE_FAILURE "
                        + reason
                        + " for "
                        + player.getName(),
                failure);
        String alert =
                "ScenarioCraft recovery persistence alert: "
                        + player.getName()
                        + " still needs a confirmed, saved hub recovery. Keep them contained and check the server log.";
        server.getConsoleSender().sendMessage(alert);
        for (Player onlinePlayer : server.getOnlinePlayers()) {
            if (receivesOperatorAlerts(onlinePlayer)) {
                onlinePlayer.sendMessage(alert);
            }
        }
    }

    private void reportRecoveryRegistryFailure(
            Player player, String reason, RuntimeException failure) {
        logger.log(
                Level.SEVERE,
                "SCENARIOCRAFT_RECOVERY_REGISTRY_FAILURE registry "
                        + recoveryStore.location()
                        + " "
                        + reason
                        + " for "
                        + player.getName(),
                failure);
        String alert =
                "ScenarioCraft recovery registry alert: "
                        + player.getName()
                        + " still needs a confirmed, saved hub recovery. Check atomic-move support and the registry path in the server log.";
        server.getConsoleSender().sendMessage(alert);
        for (Player onlinePlayer : server.getOnlinePlayers()) {
            if (receivesOperatorAlerts(onlinePlayer)) {
                onlinePlayer.sendMessage(alert);
            }
        }
    }

    private void restorePendingInventory(Player player) {
        restorePendingInventory(player, () -> {});
    }

    private void restorePendingInventory(
            Player player, Runnable afterRecovery) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        byte[] encoded =
                data.get(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
        if (encoded == null) {
            return;
        }
        markStrandedForRecovery(player);
        boolean inventoryRestored = false;
        try {
            restoreInventorySnapshot(player, decodeInventorySnapshot(encoded));
            inventoryRestored = true;
            recoveryGameModes.put(player.getUniqueId(), player.getGameMode());
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
                    finishHubRecovery(
                            player,
                            () -> {
                                resetPersonalBorder(player);
                                if (restored) {
                                    logger.info(
                                            "Restored pending round inventory for "
                                                    + player.getName()
                                                    + ".");
                                }
                                afterRecovery.run();
                            });
                },
                () -> {
                    markStrandedForRecovery(player);
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

    private static final class TeleportAttempt {
        private final Player player;
        private final Location destination;
        private final Runnable onSuccess;
        private final Runnable onFailure;
        private final Set<BukkitTask> tasks = new LinkedHashSet<>();
        private boolean completed;

        private TeleportAttempt(
                Player player,
                Location destination,
                Runnable onSuccess,
                Runnable onFailure) {
            this.player = Objects.requireNonNull(player, "player");
            this.destination = Objects.requireNonNull(destination, "destination");
            this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
            this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        }

        private Player player() {
            return player;
        }

        private Location destination() {
            return destination;
        }

        private Runnable onSuccess() {
            return onSuccess;
        }

        private Runnable onFailure() {
            return onFailure;
        }

        private Set<BukkitTask> tasks() {
            return tasks;
        }

        private boolean completed() {
            return completed;
        }

        private void complete() {
            completed = true;
        }
    }

    private record Contestant(
            UUID playerId,
            String playerName,
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
