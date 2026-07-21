package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Replays durable results and polls for one automatic announcement during REVEAL. */
public final class ResultAnnouncementService implements BattleResultsReporter, AutoCloseable {
    private static final String NO_RESULTS_MESSAGE =
            "No judge results yet — check back after the reveal!";
    private static final String READ_FAILURE_MESSAGE =
            "The latest judge results need a grown-up helper to check the results file.";

    private final BattleRound round;
    private final Server server;
    private final Logger logger;
    private final BattleResultsReader reader;
    private final Function<String, Location> celebrationLocation;
    private final BukkitTask pollTask;
    private String announcedFingerprint;
    private String loggedReadFailure;

    ResultAnnouncementService(
            Plugin plugin,
            BattleRound round,
            BattleResultsReader reader,
            Function<String, Location> celebrationLocation,
            long pollTicks) {
        this.round = Objects.requireNonNull(round, "round");
        this.server = Objects.requireNonNull(plugin.getServer(), "server");
        this.logger = Objects.requireNonNull(plugin.getLogger(), "logger");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.celebrationLocation =
                Objects.requireNonNull(celebrationLocation, "celebrationLocation");
        if (pollTicks < 1L) {
            throw new IllegalArgumentException("results poll ticks must be positive");
        }
        this.pollTask = server.getScheduler().runTaskTimer(plugin, this::poll, pollTicks, pollTicks);
    }

    public static ResultAnnouncementService forPlugin(
            Plugin plugin,
            BattleRound round,
            Function<String, Location> celebrationLocation,
            long pollTicks) {
        Path roundsDirectory = plugin.getDataFolder().toPath().resolve("rounds");
        return new ResultAnnouncementService(
                plugin,
                round,
                new BattleResultsReader(roundsDirectory),
                celebrationLocation,
                pollTicks);
    }

    @Override
    public void replayLatest(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        try {
            Optional<BattleResultsReader.LatestResult> latest = reader.latest();
            if (latest.isEmpty()) {
                sender.sendMessage(NO_RESULTS_MESSAGE);
                return;
            }
            ResultAnnouncementFormatter.Announcement announcement =
                    ResultAnnouncementFormatter.format(latest.get().summary());
            announcement.chatLines().forEach(sender::sendMessage);
        } catch (IOException | IllegalArgumentException failure) {
            logReadFailure(failure);
            sender.sendMessage(READ_FAILURE_MESSAGE);
        }
    }

    @Override
    public void announceLatest(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (round.phase() != RoundPhase.REVEAL) {
            sender.sendMessage(
                    "Judge results are safe on disk; the in-game celebration only plays during REVEAL.");
            return;
        }
        try {
            Optional<BattleResultsReader.LatestResult> latest = reader.latestRound();
            if (latest.isEmpty()) {
                sender.sendMessage(NO_RESULTS_MESSAGE);
                return;
            }
            if (announce(latest.get())) {
                sender.sendMessage("ScenarioCraft announced the latest judge results.");
            } else {
                sender.sendMessage("ScenarioCraft already announced these judge results.");
            }
        } catch (IOException | IllegalArgumentException failure) {
            logReadFailure(failure);
            sender.sendMessage(READ_FAILURE_MESSAGE);
        }
    }

    void poll() {
        if (round.phase() != RoundPhase.REVEAL) {
            return;
        }
        try {
            Optional<BattleResultsReader.LatestResult> latest = reader.latestRound();
            latest.ifPresent(this::announce);
            loggedReadFailure = null;
        } catch (IOException | IllegalArgumentException failure) {
            logReadFailure(failure);
        }
    }

    private boolean announce(BattleResultsReader.LatestResult latest) {
        if (latest.fingerprint().equals(announcedFingerprint)) {
            return false;
        }
        ResultAnnouncementFormatter.Announcement announcement =
                ResultAnnouncementFormatter.format(latest.summary());
        for (Player player : server.getOnlinePlayers()) {
            announcement.chatLines().forEach(player::sendMessage);
            player.sendTitle("§6" + announcement.title(), "§fWarm words from the judges", 10, 70, 20);
        }
        if (latest.summary().hasWinner()) {
            celebrate(latest.summary().winner().plotId());
        }
        announcedFingerprint = latest.fingerprint();
        loggedReadFailure = null;
        logger.info(
                "SCENARIOCRAFT_RESULTS_ANNOUNCED "
                        + latest.summary().roundId()
                        + " chat_lines="
                        + announcement.chatLines().size());
        return true;
    }

    private void celebrate(String plotId) {
        Location location = celebrationLocation.apply(plotId);
        if (location == null) {
            logger.warning(
                    "Judge results named " + plotId + ", but its celebration location is unavailable.");
            return;
        }
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FIREWORK, location, 40, 2.5, 2.0, 2.5, 0.08);
        }
    }

    private void logReadFailure(RuntimeException failure) {
        logReadFailure((Exception) failure);
    }

    private void logReadFailure(Exception failure) {
        String signature = failure.getClass().getName() + ":" + failure.getMessage();
        if (!signature.equals(loggedReadFailure)) {
            loggedReadFailure = signature;
            logger.log(Level.WARNING, "Could not read kid-facing judge results", failure);
        }
    }

    @Override
    public void close() {
        pollTask.cancel();
    }
}
