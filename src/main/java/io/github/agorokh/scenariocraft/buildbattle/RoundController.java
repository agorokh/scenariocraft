package io.github.agorokh.scenariocraft.buildbattle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

    private final Server server;
    private final BattleSettings settings;
    private final ArenaWorld arena;
    private final BatchedBlockEditor blockEditor;
    private final Logger logger;
    private final RoundStateMachine state = new RoundStateMachine();
    private final Map<UUID, Contestant> contestants = new LinkedHashMap<>();
    private final Map<UUID, Spectator> revealSpectators = new LinkedHashMap<>();
    private final NamespacedKey inventorySnapshotKey;
    private final BossBar buildBossBar;
    private final BukkitTask timerTask;
    private List<PlotBounds> plots = List.of();
    private RoundTimer timer;
    private CommandSender roundStarter;
    private boolean closed;

    public RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.blockEditor = Objects.requireNonNull(blockEditor, "blockEditor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.inventorySnapshotKey =
                new NamespacedKey(plugin, "round-inventory-snapshot");
        this.buildBossBar =
                server.createBossBar(
                        "Build time", BarColor.BLUE, BarStyle.SOLID);
        buildBossBar.setVisible(false);
        server.getPluginManager().registerEvents(this, plugin);
        this.timerTask =
                server.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Player player : server.getOnlinePlayers()) {
            restorePendingInventory(player);
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
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
                return;
            }
            applyCurrentPhase(player, contestant);
            return;
        }
        restorePendingInventory(player);
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        buildBossBar.removePlayer(player);
        Contestant contestant = contestants.get(player.getUniqueId());
        if (contestant != null) {
            restoreContestantToHub(player, contestant);
        }
        Spectator spectator = revealSpectators.get(player.getUniqueId());
        if (spectator != null) {
            restoreSpectator(player, spectator);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (phase() != RoundPhase.IDLE
                && contestants.containsKey(event.getPlayer().getUniqueId())) {
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
        roundStarter = null;
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
            case NOTE_PICK -> beginBuilding();
            case BUILDING -> beginReveal();
            case REVEAL -> completeRound();
            case IDLE, PREPARING -> throw new IllegalStateException(
                    "untimed phase unexpectedly owned a countdown");
        }
    }

    private void beginNotePick() {
        transitionTo(RoundPhase.NOTE_PICK);
        timer = RoundTimer.start(settings.timings().noteSeconds());
        String task = settings.tasks().getFirst();
        broadcast("Your build idea is: " + task + "!");
        forEachOnlineContestant(
                (player, ignored) ->
                        player.sendTitle(
                                "Build idea!",
                                task,
                                TELEPORT_FADE_TICKS,
                                TITLE_STAY_TICKS,
                                TELEPORT_FADE_TICKS));
    }

    private void beginBuilding() {
        transitionTo(RoundPhase.BUILDING);
        timer = RoundTimer.start(settings.timings().buildSeconds());
        buildBossBar.setVisible(true);
        forEachOnlineContestant(this::moveToPlot);
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
        contestants.clear();
        revealSpectators.clear();
        roundStarter = null;
        plots = List.of();
        broadcast("Build Battle complete — amazing creating, everyone!");
        logger.info("Round complete: returned to IDLE.");
    }

    private void abortRound() {
        blockEditor.cancel();
        timer = null;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        restoreRoundPlayers();
        transitionTo(RoundPhase.IDLE);
        contestants.clear();
        revealSpectators.clear();
        roundStarter = null;
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
                moveToHub(player, contestant);
                player.sendTitle(
                        "Build idea!",
                        settings.tasks().getFirst(),
                        TELEPORT_FADE_TICKS,
                        TITLE_STAY_TICKS,
                        TELEPORT_FADE_TICKS);
            }
            case BUILDING -> moveToPlot(player, contestant);
            case REVEAL -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.teleport(tourLocation());
            }
            case IDLE -> {
                // The caller excludes IDLE; retaining this arm keeps the phase handling exhaustive.
            }
        }
    }

    private void moveToHub(Player player, Contestant ignored) {
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(hubLocation());
        buildBossBar.removePlayer(player);
    }

    private void moveToPlot(Player player, Contestant contestant) {
        clearRoundInventory(player);
        player.setGameMode(GameMode.CREATIVE);
        PlotBounds plot = contestant.plot();
        player.teleport(
                new Location(
                        arena.world(),
                        plot.centerX() + 0.5,
                        arena.floorY() + 1.0,
                        plot.centerZ() + 0.5));
        buildBossBar.addPlayer(player);
    }

    private void prepareContestant(Player player, Contestant contestant) {
        clearRoundInventory(player);
        moveToHub(player, contestant);
    }

    private void moveContestantToTour(Player player, Contestant ignored) {
        clearRoundInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(tourLocation());
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
        restoreInventorySnapshot(player, contestant.inventorySnapshot());
        player.teleport(hubLocation());
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
        player.teleport(tourLocation());
    }

    private void restoreSpectator(Player player, Spectator spectator) {
        player.setGameMode(spectator.originalGameMode());
        player.teleport(spectator.originalLocation());
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

    private Location hubLocation() {
        Location spawn = arena.world().getSpawnLocation();
        return new Location(
                arena.world(),
                spawn.getBlockX() + 0.5,
                arena.floorY() + 1.0,
                spawn.getBlockZ() + 0.5);
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
                cursor == null ? null : cursor.clone());
    }

    private void persistInventorySnapshot(Player player, InventorySnapshot snapshot) {
        player.getPersistentDataContainer()
                .set(
                        inventorySnapshotKey,
                        PersistentDataType.BYTE_ARRAY,
                        encodeInventorySnapshot(snapshot));
        player.saveData();
    }

    private void restorePendingInventory(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        byte[] encoded =
                data.get(inventorySnapshotKey, PersistentDataType.BYTE_ARRAY);
        if (encoded == null) {
            return;
        }
        try {
            restoreInventorySnapshot(player, decodeInventorySnapshot(encoded));
            player.teleport(hubLocation());
            logger.info("Restored pending round inventory for " + player.getName() + ".");
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "Could not restore pending round inventory for " + player.getName(),
                    failure);
            player.sendMessage(
                    "Your saved items need a grown-up helper before the next battle.");
        }
    }

    private void restoreInventorySnapshot(Player player, InventorySnapshot snapshot) {
        clearRoundInventory(player);
        player.getInventory().setContents(cloneContents(snapshot.inventoryContents()));
        player.getEnderChest().setContents(cloneContents(snapshot.enderChestContents()));
        player.setItemOnCursor(
                snapshot.cursorItem() == null ? null : snapshot.cursorItem().clone());
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
        output.writeBoolean(item != null);
        if (item == null) {
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
            snapshot[slot] = item == null ? null : item.clone();
        }
        return snapshot;
    }

    private static String formatTime(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String friendlyPhase(RoundPhase phase) {
        return phase.name().toLowerCase().replace('_', ' ');
    }

    private record Contestant(
            UUID playerId, PlotBounds plot, InventorySnapshot inventorySnapshot) {}

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
