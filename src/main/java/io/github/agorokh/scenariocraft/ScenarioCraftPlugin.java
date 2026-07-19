package io.github.agorokh.scenariocraft;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * ScenarioCraft's Paper entry point.
 *
 * <p>Gameplay is intentionally introduced by later Build Battle issues.
 */
public final class ScenarioCraftPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("ScenarioCraft enabled — ready for kid-invented scenarios.");
    }
}
