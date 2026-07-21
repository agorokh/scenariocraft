package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Loads Build Battle settings from config.yml and rejects unusable values early. */
public final class ArenaConfigLoader {
    private static final int DEBUG_PLOT_COUNT = 2;
    private static final int MAX_PLOT_COUNT = 8;
    private static final int DEFAULT_RESULTS_POLL_TICKS = 20;

    private ArenaConfigLoader() {}

    public static BattleSettings load(FileConfiguration config) {
        ArenaSettings arena =
                new ArenaSettings(
                        positiveInt(config, "plot-size"),
                        positiveInt(config, "wall-height"),
                        positiveInt(config, "plot-spacing"),
                        inRange(config, "max-plots", DEBUG_PLOT_COUNT, MAX_PLOT_COUNT),
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
                config.getBoolean("allow-any-start"),
                positiveIntOrDefault(
                        config, "results-poll-ticks", DEFAULT_RESULTS_POLL_TICKS));
    }

    private static int positiveIntOrDefault(
            FileConfiguration config, String path, int defaultValue) {
        if (!config.contains(path, true)) {
            return defaultValue;
        }
        return positiveInt(config, path);
    }

    public static ResultAnnouncementSettings loadResultAnnouncements(
            FileConfiguration config, BattleSettings settings) {
        return new ResultAnnouncementSettings(
                settings.resultsPollTicks(),
                inRangeOrDefault(config, "results-celebration-bursts", 1, 10, 3),
                inRangeOrDefault(
                        config, "results-celebration-interval-ticks", 1, 100, 10));
    }

    private static int inRangeOrDefault(
            FileConfiguration config,
            String path,
            int minimum,
            int maximum,
            int defaultValue) {
        return config.isSet(path) ? inRange(config, path, minimum, maximum) : defaultValue;
    }

    private static int positiveInt(FileConfiguration config, String path) {
        return atLeast(config, path, 1);
    }

    private static int inRange(
            FileConfiguration config, String path, int minimum, int maximum) {
        int value = atLeast(config, path, minimum);
        if (value > maximum) {
            throw new IllegalArgumentException(path + " must be at most " + maximum);
        }
        return value;
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
