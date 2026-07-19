package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Warns about installed plugins that commonly protect land from block edits. */
public final class ProtectionPluginWarner {
    private static final Set<String> KNOWN_PROTECTION_PLUGINS =
            Set.of(
                    "worldguard",
                    "griefprevention",
                    "plotsquared",
                    "towny",
                    "lands",
                    "residence",
                    "husktowns",
                    "huskclaims");

    private ProtectionPluginWarner() {}

    public static void warnIfPresent(
            PluginManager pluginManager, Logger logger, String worldName) {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (mayProtectLand(plugin.getName())) {
                logger.warning(
                        plugin.getName()
                                + " may prevent arena block edits in "
                                + worldName
                                + "; exempt this world before starting a battle.");
            }
        }
    }

    static boolean mayProtectLand(String pluginName) {
        String normalized = pluginName.toLowerCase(Locale.ROOT).replace("-", "");
        return KNOWN_PROTECTION_PLUGINS.contains(normalized)
                || normalized.contains("protect")
                || normalized.contains("claim");
    }
}
