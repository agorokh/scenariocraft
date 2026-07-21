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
    private final BattleResultReader repository;
    private final BattleResultFormatter formatter = new BattleResultFormatter();
    private final ResultAnnouncementSettings settings;
    private final Supplier<RoundPhase> phase;
    private final Supplier<Optional<String>> resultRoundId;
    private final Function<String, Optional<Location>> winnerLocation;
    private final Logger logger;
    private final AtomicBoolean commandReadInFlight = new AtomicBoolean();
    private final AtomicBoolean announcementReadInFlight = new AtomicBoolean();
    private final AtomicBoolean pollReadInFlight = new AtomicBoolean();
    private final BukkitTask pollingTask;
    private BattleResult announcedResult;
    private String pollingRoundId;
    private long ticksUntilPoll;
    private boolean closed;

    public BattleResultService(
            Plugin plugin,
            Path roundsDirectory,
            ResultAnnouncementSettings settings,
            Supplier<RoundPhase> phase,
            Supplier<Optional<String>> resultRoundId,
            Function<String, Optional<Location>> winnerLocation,
            Logger logger) {
        this(
                plugin,
                new BattleResultRepository(roundsDirectory),
                settings,
                phase,
                resultRoundId,
                winnerLocation,
                logger);
    }

    BattleResultService(
            Plugin plugin,
            BattleResultReader repository,
            ResultAnnouncementSettings settings,
            Supplier<RoundPhase> phase,
            Supplier<Optional<String>> resultRoundId,
            Function<String, Optional<Location>> winnerLocation,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.resultRoundId = Objects.requireNonNull(resultRoundId, "resultRoundId");
        this.winnerLocation = Objects.requireNonNull(winnerLocation, "winnerLocation");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollingTask =
                server.getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::pollDuringReveal,
                                1L,
                                1L);
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
                sender,
                commandReadInFlight);
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
                    if (phase.get() != RoundPhase.REVEAL
                            || resultRoundId.get().filter(roundId::equals).isEmpty()) {
                        sender.sendMessage(
                                "That judged round is no longer the active reveal, so it was not announced.");
                        return;
                    }
                    if (announce(result.orElseThrow())) {
                        sender.sendMessage("ScenarioCraft announced " + roundId + ".");
                    } else {
                        sender.sendMessage("ScenarioCraft already announced " + roundId + ".");
                    }
                },
                sender,
                announcementReadInFlight);
    }

    private void pollDuringReveal() {
        if (closed || phase.get() != RoundPhase.REVEAL) {
            pollingRoundId = null;
            ticksUntilPoll = 0L;
            return;
        }
        Optional<String> expectedRound = resultRoundId.get();
        if (expectedRound.isEmpty()) {
            return;
        }
        String expectedRoundId = expectedRound.orElseThrow();
        if (!expectedRoundId.equals(pollingRoundId)) {
            pollingRoundId = expectedRoundId;
            ticksUntilPoll = 0L;
        }
        if (pollReadInFlight.get()) {
            return;
        }
        if (ticksUntilPoll > 0L) {
            ticksUntilPoll--;
            return;
        }
        ticksUntilPoll = settings.pollTicks();
        readAsync(
                () -> repository.round(expectedRoundId),
                result -> {
                    if (phase.get() == RoundPhase.REVEAL
                            && resultRoundId.get().filter(expectedRoundId::equals).isPresent()) {
                        result.ifPresent(this::announce);
                    }
                },
                null,
                pollReadInFlight);
    }

    private boolean announce(BattleResult result) {
        if (closed || result.equals(announcedResult)) {
            return false;
        }
        announcedResult = result;
        List<String> lines = formatter.chatLines(result);
        String title = formatter.title(result);
        for (Player player : server.getOnlinePlayers()) {
            player.sendTitle(title, safeSubtitle(result.task()), TITLE_FADE_TICKS, TITLE_STAY_TICKS, TITLE_FADE_TICKS);
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
        result.winner()
                .flatMap(winner -> winnerLocation.apply(winner.player()))
                .ifPresent(location -> celebrate(result.roundId(), location));
        logger.info("SCENARIOCRAFT_RESULTS_ANNOUNCED " + result.roundId());
        return true;
    }

    private void celebrate(String announcedRoundId, Location location) {
        for (int burst = 0; burst < settings.celebrationBursts(); burst++) {
            long delay = Math.multiplyExact((long) burst, settings.celebrationIntervalTicks());
            server.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!closed
                                        && phase.get() == RoundPhase.REVEAL
                                        && resultRoundId
                                                .get()
                                                .filter(announcedRoundId::equals)
                                                .isPresent()
                                        && location.getWorld() != null) {
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
            CommandSender failureRecipient,
            AtomicBoolean readGate) {
        if (closed || !readGate.compareAndSet(false, true)) {
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
                                } catch (RuntimeException exception) {
                                    failure =
                                            new IOException(
                                                    "Unexpected failure while reading judge results",
                                                    exception);
                                }
                                Optional<BattleResult> completedResult = result;
                                IOException completedFailure = failure;
                                try {
                                    server.getScheduler()
                                            .runTask(
                                            plugin,
                                                    () ->
                                                            finishRead(
                                                                    completedResult,
                                                                    completedFailure,
                                                                    completion,
                                                                    failureRecipient,
                                                                    readGate));
                                } catch (RuntimeException schedulingFailure) {
                                    readGate.set(false);
                                    logger.log(Level.WARNING, "Could not return judge results to the server thread", schedulingFailure);
                                }
                            });
        } catch (RuntimeException schedulingFailure) {
            readGate.set(false);
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
            CommandSender failureRecipient,
            AtomicBoolean readGate) {
        readGate.set(false);
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
