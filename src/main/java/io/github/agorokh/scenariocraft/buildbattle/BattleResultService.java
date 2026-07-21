package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Reads result files off-thread and owns deduplicated player presentation. */
public final class BattleResultService implements BattleResultCommands, AutoCloseable {
    private static final int TITLE_FADE_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 70;

    private final Plugin plugin;
    private final Server server;
    private final BattleResultRepository repository;
    private final BattleResultFormatter formatter = new BattleResultFormatter();
    private final ResultAnnouncementSettings settings;
    private final Supplier<RoundPhase> phase;
    private final Function<String, Optional<Location>> winnerLocation;
    private final Logger logger;
    private final AtomicBoolean readInFlight = new AtomicBoolean();
    private final BukkitTask pollingTask;
    private String announcedRoundId;
    private boolean closed;

    public BattleResultService(
            Plugin plugin,
            Path roundsDirectory,
            ResultAnnouncementSettings settings,
            Supplier<RoundPhase> phase,
            Function<String, Optional<Location>> winnerLocation,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.repository = new BattleResultRepository(roundsDirectory);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.winnerLocation = Objects.requireNonNull(winnerLocation, "winnerLocation");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollingTask =
                server.getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::pollDuringReveal,
                                settings.pollTicks(),
                                settings.pollTicks());
    }

    @Override
    public void showLatest(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        readAsync(
                repository::latest,
                result -> {
                    if (result.isEmpty()) {
                        sender.sendMessage("No judging results are ready yet — check back after the reveal!");
                        return;
                    }
                    for (String line : formatter.chatLines(result.orElseThrow())) {
                        sender.sendMessage(line);
                    }
                },
                sender);
    }

    @Override
    public void announceRound(String roundId, CommandSender sender) {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(sender, "sender");
        if (!roundId.matches("round-[0-9]{8}-[0-9]{6}")) {
            sender.sendMessage("Round id must look like round-20260721-193000.");
            return;
        }
        readAsync(
                () -> repository.round(roundId),
                result -> {
                    if (result.isEmpty()) {
                        sender.sendMessage("That round does not have judging results yet.");
                        return;
                    }
                    announce(result.orElseThrow());
                    sender.sendMessage("ScenarioCraft announced " + roundId + ".");
                },
                sender);
    }

    private void pollDuringReveal() {
        if (closed || phase.get() != RoundPhase.REVEAL || readInFlight.get()) {
            return;
        }
        readAsync(
                repository::latest,
                result -> {
                    if (phase.get() == RoundPhase.REVEAL) {
                        result.ifPresent(this::announce);
                    }
                },
                null);
    }

    private void announce(BattleResult result) {
        if (closed || result.roundId().equals(announcedRoundId)) {
            return;
        }
        announcedRoundId = result.roundId();
        List<String> lines = formatter.chatLines(result);
        String title = formatter.title(result);
        for (Player player : server.getOnlinePlayers()) {
            player.sendTitle(title, safeSubtitle(result.task()), TITLE_FADE_TICKS, TITLE_STAY_TICKS, TITLE_FADE_TICKS);
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
        result.winner().flatMap(winner -> winnerLocation.apply(winner.player())).ifPresent(this::celebrate);
        logger.info("Announced judge results for " + result.roundId() + ".");
    }

    private void celebrate(Location location) {
        for (int burst = 0; burst < settings.celebrationBursts(); burst++) {
            long delay = Math.multiplyExact((long) burst, settings.celebrationIntervalTicks());
            server.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!closed && location.getWorld() != null) {
                                    location.getWorld()
                                            .spawnParticle(
                                                    Particle.HAPPY_VILLAGER,
                                                    location,
                                                    35,
                                                    2.5,
                                                    1.5,
                                                    2.5,
                                                    0.05);
                                }
                            },
                            delay);
        }
    }

    private void readAsync(
            ResultRead read,
            java.util.function.Consumer<Optional<BattleResult>> completion,
            CommandSender failureRecipient) {
        if (closed || !readInFlight.compareAndSet(false, true)) {
            if (failureRecipient != null) {
                failureRecipient.sendMessage("The results are already being checked — just a moment!");
            }
            return;
        }
        try {
            server.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                Optional<BattleResult> result = Optional.empty();
                                IOException failure = null;
                                try {
                                    result = read.read();
                                } catch (IOException exception) {
                                    failure = exception;
                                }
                                Optional<BattleResult> completedResult = result;
                                IOException completedFailure = failure;
                                try {
                                    server.getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> finishRead(completedResult, completedFailure, completion, failureRecipient));
                                } catch (RuntimeException schedulingFailure) {
                                    readInFlight.set(false);
                                    logger.log(Level.WARNING, "Could not return judge results to the server thread", schedulingFailure);
                                }
                            });
        } catch (RuntimeException schedulingFailure) {
            readInFlight.set(false);
            logger.log(Level.WARNING, "Could not schedule judge result reading", schedulingFailure);
            if (failureRecipient != null) {
                failureRecipient.sendMessage("The judging results could not be checked right now.");
            }
        }
    }

    private void finishRead(
            Optional<BattleResult> result,
            IOException failure,
            java.util.function.Consumer<Optional<BattleResult>> completion,
            CommandSender failureRecipient) {
        readInFlight.set(false);
        if (closed) {
            return;
        }
        if (failure != null) {
            logger.log(Level.WARNING, "Could not read judge results", failure);
            if (failureRecipient != null) {
                failureRecipient.sendMessage("The judging results could not be read safely right now.");
            }
            return;
        }
        completion.accept(result);
    }

    private static String safeSubtitle(String task) {
        String safe = task.replace('{', '(').replace('}', ')').replace('[', '(').replace(']', ')');
        return safe.length() <= BattleResultFormatter.MAX_TITLE_LENGTH
                ? safe
                : safe.substring(0, BattleResultFormatter.MAX_TITLE_LENGTH - 1).stripTrailing() + "…";
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pollingTask.cancel();
    }

    @FunctionalInterface
    private interface ResultRead {
        Optional<BattleResult> read() throws IOException;
    }
}
