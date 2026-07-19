package io.github.agorokh.scenariocraft.buildbattle;

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
import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Applies the validated phase machine to Paper players, timers, and arena work. */
public final class RoundController implements BattleRound, Listener, AutoCloseable {
    private static final int MINIMUM_DEBUG_PLOTS = 2;
    private static final int TELEPORT_FADE_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 50;

    private final Plugin plugin;
    private final Server server;
    private final BattleSettings settings;
    private final ArenaWorld arena;
    private final BatchedBlockEditor blockEditor;
    private final Logger logger;
    private final RoundStateMachine state = new RoundStateMachine();
    private final Map<UUID, Contestant> contestants = new LinkedHashMap<>();
    private final BossBar buildBossBar;
    private final BukkitTask timerTask;
    private List<PlotBounds> plots = List.of();
    private RoundTimer timer;
    private boolean closed;

    public RoundController(
            Plugin plugin,
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.blockEditor = Objects.requireNonNull(blockEditor, "blockEditor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.buildBossBar =
                server.createBossBar(
                        "Build time", BarColor.BLUE, BarStyle.SOLID);
        buildBossBar.setVisible(false);
        server.getPluginManager().registerEvents(this, plugin);
        this.timerTask =
                server.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
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
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            contestants.put(
                    player.getUniqueId(),
                    new Contestant(player.getUniqueId(), plots.get(index), player.getGameMode()));
        }

        transitionTo(RoundPhase.PREPARING);
        sender.sendMessage(
                players.size() < MINIMUM_DEBUG_PLOTS
                        ? "Starting with "
                                + players.size()
                                + " builder and debug fill for two plots."
                        : "Starting Build Battle for " + players.size() + " builders!");
        broadcast("Build Battle is getting the arena ready in safe little batches!");

        long mutations =
                blockEditor.enqueueArena(
                        plots,
                        arena.floorY(),
                        arenaSettings.wallHeight(),
                        completedMutations -> arenaResetComplete(completedMutations),
                        this::arenaWorkFailed);
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
        Contestant contestant = contestants.get(event.getPlayer().getUniqueId());
        if (contestant != null && phase() != RoundPhase.IDLE) {
            applyCurrentPhase(event.getPlayer(), contestant);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        buildBossBar.removePlayer(event.getPlayer());
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
        restoreContestantsToHub();
        contestants.clear();
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
        } else if (timer.shouldAnnounceShortCountdown()) {
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
        for (Player player : server.getOnlinePlayers()) {
            player.teleport(tourLocation());
        }
        forEachOnlineContestant(
                (player, ignored) -> player.setGameMode(GameMode.ADVENTURE));
        broadcast("Time to reveal the builds! The walls are coming down safely.");

        ArenaSettings arenaSettings = settings.arena();
        long mutations =
                blockEditor.enqueueWallRemoval(
                        plots,
                        arena.floorY(),
                        arenaSettings.wallHeight(),
                        this::wallRemovalComplete,
                        this::arenaWorkFailed);
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
        restoreContestantsToHub();
        transitionTo(RoundPhase.IDLE);
        timer = null;
        contestants.clear();
        plots = List.of();
        broadcast("Build Battle complete — amazing creating, everyone!");
        logger.info("Round complete: returned to IDLE.");
    }

    private void abortRound() {
        blockEditor.cancel();
        timer = null;
        buildBossBar.removeAll();
        buildBossBar.setVisible(false);
        restoreContestantsToHub();
        transitionTo(RoundPhase.IDLE);
        contestants.clear();
        plots = List.of();
    }

    private void arenaWorkFailed(Throwable failure) {
        if (phase() == RoundPhase.IDLE) {
            return;
        }
        logger.log(Level.SEVERE, "Build Battle arena work failed; aborting round", failure);
        abortRound();
        broadcast(
                "The arena could not get ready this time. Please ask a grown-up helper to check the server.");
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
        switch (phase()) {
            case PREPARING, GATHERING, NOTE_PICK -> moveToHub(player, contestant);
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

    private void restoreContestantsToHub() {
        forEachOnlineContestant(
                (player, contestant) -> {
                    buildBossBar.removePlayer(player);
                    player.setGameMode(contestant.originalGameMode());
                    player.teleport(hubLocation());
                });
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

    private static String formatTime(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String friendlyPhase(RoundPhase phase) {
        return phase.name().toLowerCase().replace('_', ' ');
    }

    private record Contestant(UUID playerId, PlotBounds plot, GameMode originalGameMode) {}

    @FunctionalInterface
    private interface OnlineContestantAction {
        void accept(Player player, Contestant contestant);
    }
}
