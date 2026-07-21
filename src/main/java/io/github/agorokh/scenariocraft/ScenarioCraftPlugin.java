package io.github.agorokh.scenariocraft;

import io.github.agorokh.scenariocraft.buildbattle.ArenaConfigLoader;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorld;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorldService;
import io.github.agorokh.scenariocraft.buildbattle.BatchedBlockEditor;
import io.github.agorokh.scenariocraft.buildbattle.BattleCommand;
import io.github.agorokh.scenariocraft.buildbattle.BattleSettings;
import io.github.agorokh.scenariocraft.buildbattle.ProtectionPluginWarner;
import io.github.agorokh.scenariocraft.buildbattle.RoundController;
import io.github.agorokh.scenariocraft.buildbattle.TeleportRecoveryStore;
import io.github.agorokh.scenariocraft.buildbattle.TeleportTransport;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

/** ScenarioCraft's Paper entry point. */
public final class ScenarioCraftPlugin extends JavaPlugin {
    private BatchedBlockEditor blockEditor;
    private RoundController roundController;

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

        blockEditor =
                new BatchedBlockEditor(
                        this, arena.world(), settings.arena().blocksPerTick(), getLogger());
        TeleportRecoveryStore recoveryStore;
        try {
            recoveryStore =
                    TeleportRecoveryStore.open(
                            getDataFolder().toPath().resolve("pending-teleport-recovery.txt"));
        } catch (IllegalStateException failure) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "SCENARIOCRAFT_RECOVERY_PERSISTENCE_FAILURE Could not load teleport recovery registry. Stop the server, back up the registry, and follow the recovery runbook in README.md before enabling ScenarioCraft again.",
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
                        recoveryStore);
        BattleCommand battleCommand = new BattleCommand(settings, roundController);
        Objects.requireNonNull(getCommand("battle"), "battle command missing from plugin.yml")
                .setExecutor(battleCommand);

        getLogger()
                .info(
                        "Loaded "
                                + settings.tasks().size()
                                + " Build Battle tasks; plot size "
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

    @Override
    public void onDisable() {
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
