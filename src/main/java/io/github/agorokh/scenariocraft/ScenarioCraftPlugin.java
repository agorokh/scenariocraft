package io.github.agorokh.scenariocraft;

import io.github.agorokh.scenariocraft.buildbattle.ArenaConfigLoader;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorld;
import io.github.agorokh.scenariocraft.buildbattle.ArenaWorldService;
import io.github.agorokh.scenariocraft.buildbattle.BatchedBlockEditor;
import io.github.agorokh.scenariocraft.buildbattle.BattleCommand;
import io.github.agorokh.scenariocraft.buildbattle.BattleSettings;
import io.github.agorokh.scenariocraft.buildbattle.ProtectionPluginWarner;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** ScenarioCraft's Paper entry point. */
public final class ScenarioCraftPlugin extends JavaPlugin {
    private BatchedBlockEditor blockEditor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BattleSettings settings = ArenaConfigLoader.load(getConfig());

        ArenaWorld arena = new ArenaWorldService(getServer(), getLogger()).loadOrCreate();
        int topWallY = Math.addExact(arena.floorY(), settings.arena().wallHeight());
        if (topWallY >= arena.world().getMaxHeight()) {
            throw new IllegalArgumentException(
                    "wall-height reaches beyond battle_world's maximum build height");
        }

        ProtectionPluginWarner.warnIfPresent(
                getServer().getPluginManager(), getLogger(), arena.world().getName());

        blockEditor =
                new BatchedBlockEditor(
                        this, arena.world(), settings.arena().blocksPerTick(), getLogger());
        BattleCommand battleCommand =
                new BattleCommand(settings, arena, blockEditor, getLogger());
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
        if (blockEditor != null) {
            blockEditor.close();
        }
    }
}
