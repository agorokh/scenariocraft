package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Loads every BB-02 setting from config.yml and rejects unusable values early. */
public final class ArenaConfigLoader {
    private static final int DEBUG_PLOT_COUNT = 2;

    private ArenaConfigLoader() {}

    public static BattleSettings load(FileConfiguration config) {
        ArenaSettings arena =
                new ArenaSettings(
                        positiveInt(config, "plot-size"),
                        positiveInt(config, "wall-height"),
                        positiveInt(config, "plot-spacing"),
                        atLeast(config, "max-plots", DEBUG_PLOT_COUNT),
                        positiveInt(config, "blocks-per-tick"));
        PhaseTimings timings =
                new PhaseTimings(
                        positiveInt(config, "gather-seconds"),
                        positiveInt(config, "note-seconds"),
                        positiveInt(config, "build-seconds"),
                        positiveInt(config, "reveal-linger-seconds"));

        List<String> tasks =
                config.getStringList("tasks").stream()
                        .map(String::trim)
                        .filter(task -> !task.isEmpty())
                        .toList();
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must contain at least one build idea");
        }
        if (!config.isList("exempt-names")) {
            throw new IllegalArgumentException("exempt-names must be a YAML list");
        }
        if (!config.isBoolean("allow-any-start")) {
            throw new IllegalArgumentException("allow-any-start must be true or false");
        }

        return new BattleSettings(
                arena,
                timings,
                tasks,
                config.getStringList("exempt-names"),
                config.getBoolean("allow-any-start"));
    }

    private static int positiveInt(FileConfiguration config, String path) {
        return atLeast(config, path, 1);
    }

    private static int atLeast(FileConfiguration config, String path, int minimum) {
        if (!config.isInt(path)) {
            throw new IllegalArgumentException(path + " must be a whole number");
        }
        int value = config.getInt(path);
        if (value < minimum) {
            throw new IllegalArgumentException(path + " must be at least " + minimum);
        }
        return value;
    }
}
