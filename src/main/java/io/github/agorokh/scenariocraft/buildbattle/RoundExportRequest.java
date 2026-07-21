package io.github.agorokh.scenariocraft.buildbattle;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Paper-independent metadata and plot volumes for one round export. */
record RoundExportRequest(String task, String world, List<Plot> plots) {
    private static final String PLAYER_IDENTIFIER = "(?=.{1,64}$)[._*#~+\\-]*[A-Za-z0-9_]+";

    RoundExportRequest {
        task = requireText(task, "task");
        world = requireText(world, "world");
        plots = List.copyOf(plots);
        Set<String> plotIds = new HashSet<>();
        for (Plot plot : plots) {
            Objects.requireNonNull(plot, "plot");
            if (plot.sizeY() == 0) {
                throw new IllegalArgumentException("source plot height must be positive");
            }
            if (!plotIds.add(plot.plotId())) {
                throw new IllegalArgumentException("plot ids must be unique");
            }
        }
    }

    record Plot(
            String plotId,
            String player,
            int originX,
            int originY,
            int originZ,
            int sizeX,
            int sizeY,
            int sizeZ) {
        Plot {
            plotId = requireText(plotId, "plotId");
            player = requireText(player, "player");
            if (!plotId.matches("p[1-9][0-9]*")) {
                throw new IllegalArgumentException("plotId must match p<N>");
            }
            if (!player.matches(PLAYER_IDENTIFIER)) {
                throw new IllegalArgumentException("player must be a valid player identifier");
            }
            if (sizeX <= 0 || sizeY < 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("plot sizes are invalid");
            }
            Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        }

        long blockCount() {
            return Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
