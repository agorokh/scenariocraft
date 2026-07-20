package io.github.agorokh.scenariocraft.buildbattle;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

/** Frozen schema-v1 representation of a round manifest. */
record RoundManifest(
        int schema,
        @SerializedName("round_id") String roundId,
        String task,
        String world,
        List<Plot> plots) {
    static final int SCHEMA_VERSION = 1;

    RoundManifest {
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("manifest schema must be 1");
        }
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(world, "world");
        plots = List.copyOf(plots);
    }

    record Plot(
            @SerializedName("plot_id") String plotId,
            String player,
            List<Integer> origin,
            List<Integer> size) {
        Plot {
            Objects.requireNonNull(plotId, "plotId");
            Objects.requireNonNull(player, "player");
            origin = List.copyOf(origin);
            size = List.copyOf(size);
            if (origin.size() != 3 || size.size() != 3) {
                throw new IllegalArgumentException(
                        "manifest plot origin and size must contain three integers");
            }
        }
    }
}
