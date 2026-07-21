package io.github.agorokh.scenariocraft;

import io.github.agorokh.scenariocraft.buildbattle.ArenaConfigLoader;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorld;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorldService;
import io.github.agorokh.scenariocraft.buildbattle.BatchedBlockEditor;
import io.github.agorokh.scenariocraft.buildbattle.BattleCommand;
import io.github.agorokh.scenariocraft.buildbattle.BattleSettings;
import io.github.agorokh.scenariocraft.buildbattle.BlockFill;
import io.github.agorokh.scenariocraft.buildbattle.DemoSampleBuild;
import io.github.agorokh.scenariocraft.buildbattle.PlotBounds;
import io.github.agorokh.scenariocraft.buildbattle.PlotGeometry;
import io.github.agorokh.scenariocraft.buildbattle.ProtectionPluginWarner;
import io.github.agorokh.scenariocraft.buildbattle.ResultAnnouncementService;
import io.github.agorokh.scenariocraft.buildbattle.RoundController;
import io.github.agorokh.scenariocraft.buildbattle.SecretChestPosition;
import io.github.agorokh.scenariocraft.buildbattle.TeleportRecoveryStore;
import io.github.agorokh.scenariocraft.buildbattle.TeleportTransport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

/** ScenarioCraft's Paper entry point. */
public final class ScenarioCraftPlugin extends JavaPlugin {
    private BatchedBlockEditor blockEditor;
    private RoundController roundController;
    private ResultAnnouncementService resultAnnouncements;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BattleSettings settings = ArenaConfigLoader.load(getConfig());

        ArenaWorld arena = new ArenaWorldService(getServer(), getLogger()).loadOrCreate();
        int topWallY = Math.addExact(arena.floorY(), settings.arena().wallHeight());
        int capY = Math.addExact(topWallY, 1);
        if (capY >= arena.world().getMaxHeight()) {
            throw new IllegalArgumentException(
                    "wall-height "
                            + settings.arena().wallHeight()
                            + " places battle_world's anti-peek cap at Y="
                            + capY
                            + ", but the world's exclusive max height is "
                            + arena.world().getMaxHeight()
                            + "; lower wall-height by at least "
                            + (capY - arena.world().getMaxHeight() + 1));
        }
        TeleportTransport teleportTransport = new TeleportTransport(getServer());
        if (!teleportTransport.isAvailable()) {
            throw new IllegalStateException(
                    "ScenarioCraft requires the vanilla minecraft:execute and minecraft:tp console commands; restore them in the server command configuration before enabling the plugin");
        }

        ProtectionPluginWarner.warnIfPresent(
                getServer().getPluginManager(), getLogger(), arena.world().getName());

        DemoSampleBuild demoSampleBuild = loadDemoSampleBuild();

        blockEditor =
                new BatchedBlockEditor(
                        this, arena.world(), settings.arena().blocksPerTick(), getLogger());
        Path recoveryRegistry =
                getDataFolder().toPath().resolve("pending-teleport-recovery.txt");
        TeleportRecoveryStore recoveryStore;
        try {
            recoveryStore = TeleportRecoveryStore.open(recoveryRegistry);
        } catch (IllegalStateException failure) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "SCENARIOCRAFT_RECOVERY_REGISTRY_FAILURE Could not load teleport recovery registry "
                                    + recoveryRegistry.toAbsolutePath().normalize()
                                    + ". Stop the server, back up the registry, and follow the recovery runbook in README.md before enabling ScenarioCraft again.",
                            failure);
            throw failure;
        }
        roundController =
                new RoundController(
                        this,
                        settings,
                        arena,
                        blockEditor,
                        getLogger(),
                        teleportTransport,
                        recoveryStore,
                        demoSampleBuild);
        resultAnnouncements =
                ResultAnnouncementService.forPlugin(
                        this,
                        roundController,
                        roundController::resultCelebrationLocation,
                        settings.resultsPollTicks());
        BattleCommand battleCommand =
                new BattleCommand(settings, roundController, resultAnnouncements);
        Objects.requireNonNull(
                        getCommand("speedbuild"), "speedbuild command missing from plugin.yml")
                .setExecutor(battleCommand);

        if (settings.demoMode()) {
            bootstrapDemoArena(settings, arena, demoSampleBuild);
        }

        getLogger()
                .info(
                        "Loaded "
                                + settings.tasks().size()
                                + " Speed Build tasks; plot size "
                                + settings.arena().plotSize()
                                + ", spacing "
                                + settings.arena().plotSpacing()
                                + ", block budget "
                                + settings.arena().blocksPerTick()
                                + " per tick.");
        getLogger()
                .info(
                        "ScenarioCraft enabled — battle_world is ready for kid-invented scenarios.");
    }

    private DemoSampleBuild loadDemoSampleBuild() {
        try (InputStream input = getResource("demo/sample-build.blocks")) {
            if (input == null) {
                throw new IllegalStateException("bundled demo sample build is missing");
            }
            return DemoSampleBuild.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("bundled demo sample build is invalid", failure);
        }
    }

    private void bootstrapDemoArena(
            BattleSettings settings, ArenaWorld arena, DemoSampleBuild demoSampleBuild) {
        List<PlotBounds> plots =
                PlotGeometry.aroundHub(
                        arena.world().getSpawnLocation().getBlockX(),
                        arena.world().getSpawnLocation().getBlockZ(),
                        2,
                        settings.arena().plotSize(),
                        settings.arena().plotSpacing());
        List<BlockFill> sampleFills = new ArrayList<>();
        for (PlotBounds plot : plots) {
            sampleFills.addAll(
                    demoSampleBuild.placeIn(
                            plot, arena.floorY(), settings.arena().wallHeight()));
        }
        long mutations =
                blockEditor.enqueueArena(
                        plots,
                        arena.floorY(),
                        settings.arena().wallHeight(),
                        SecretChestPosition.atHub(arena),
                        sampleFills,
                        completed ->
                                getLogger()
                                        .info(
                                                "SCENARIOCRAFT_DEMO_ARENA_READY battle_world "
                                                        + "has two sample plots after "
                                                        + completed
                                                        + " batched mutations."),
                        failure ->
                                getLogger()
                                        .log(
                                                Level.SEVERE,
                                                "SCENARIOCRAFT_DEMO_ARENA_FAILURE "
                                                        + "could not bootstrap the demo arena",
                                                failure));
        getLogger()
                .info(
                        "ScenarioCraft demo arena bootstrap queued "
                                + mutations
                                + " mutations at "
                                + settings.arena().blocksPerTick()
                                + " per tick.");
    }

    @Override
    public void onDisable() {
        if (resultAnnouncements != null) {
            resultAnnouncements.close();
        }
        if (roundController != null) {
            roundController.close();
        }
        if (blockEditor != null) {
            blockEditor.close();
        }
    }

    static boolean requiredTeleportCommandsAvailable(CommandMap commandMap) {
        return TeleportTransport.commandsAvailable(commandMap);
    }
}
