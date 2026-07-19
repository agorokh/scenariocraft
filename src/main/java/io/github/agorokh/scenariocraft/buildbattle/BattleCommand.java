package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Temporary BB-02 command that prepares two plots for geometry acceptance testing. */
public final class BattleCommand implements CommandExecutor {
    private static final int DEBUG_PLOT_COUNT = 2;

    private final BattleSettings settings;
    private final ArenaWorld arena;
    private final BatchedBlockEditor blockEditor;
    private final Logger logger;

    public BattleCommand(
            BattleSettings settings,
            ArenaWorld arena,
            BatchedBlockEditor blockEditor,
            Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.blockEditor = Objects.requireNonNull(blockEditor, "blockEditor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length != 1 || !arguments[0].equalsIgnoreCase("start")) {
            sender.sendMessage("Try /" + label + " start to get two build plots ready.");
            return true;
        }
        if (!settings.canStart(sender.getName(), sender.isOp())) {
            sender.sendMessage("A grown-up helper needs to start this battle.");
            return true;
        }
        if (blockEditor.isBusy()) {
            sender.sendMessage(
                    "The arena is already getting ready — just a few more blocks to go!");
            return true;
        }

        ArenaSettings arenaSettings = settings.arena();
        List<PlotBounds> plots =
                PlotGeometry.aroundHub(
                        arena.world().getSpawnLocation().getBlockX(),
                        arena.world().getSpawnLocation().getBlockZ(),
                        DEBUG_PLOT_COUNT,
                        arenaSettings.plotSize(),
                        arenaSettings.plotSpacing());
        long mutations =
                blockEditor.enqueueArena(
                        plots,
                        arena.floorY(),
                        arenaSettings.wallHeight(),
                        completedMutations ->
                                announceCompletion(sender, completedMutations));
        long ticks =
                BatchedWorkQueue.ticksRequired(mutations, arenaSettings.blocksPerTick());
        sender.sendMessage(
                "Getting two build plots ready in little batches — this should take about "
                        + ticks
                        + " ticks.");
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
        return true;
    }

    private void announceCompletion(CommandSender sender, long mutations) {
        String message =
                "Arena build complete: "
                        + DEBUG_PLOT_COUNT
                        + " plots in "
                        + arena.world().getName()
                        + " ("
                        + mutations
                        + " block mutations).";
        logger.info(message);
        sender.sendMessage("Two build plots are ready. Let the creativity begin!");
    }
}
