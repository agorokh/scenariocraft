package io.github.agorokh.scenariocraft.buildbattle;

/** Geometry and mutation-budget settings for the Build Battle arena. */
public record ArenaSettings(
        int plotSize, int wallHeight, int plotSpacing, int maxPlots, int blocksPerTick) {
    public ArenaSettings {
        if (plotSize <= 0
                || wallHeight <= 0
                || plotSpacing <= 0
                || maxPlots <= 0
                || blocksPerTick <= 0) {
            throw new IllegalArgumentException("arena settings must be positive");
        }
        if ((long) plotSpacing <= (long) plotSize + 1L) {
            throw new IllegalArgumentException(
                    "plot-spacing must leave room for plots and their walls");
        }
    }
}
