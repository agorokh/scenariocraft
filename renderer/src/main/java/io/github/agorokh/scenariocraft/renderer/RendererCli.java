package io.github.agorokh.scenariocraft.renderer;

import java.nio.file.Path;

public final class RendererCli {
    private RendererCli() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"--in".equals(arguments[0]) || !"--out".equals(arguments[2])) {
            System.err.println("Usage: renderer --in <pN.voxels.json> --out <directory>");
            System.exit(2);
            return;
        }
        VoxelPlot plot = VoxelPlot.read(Path.of(arguments[1]));
        new VoxelRenderer().render(plot, Path.of(arguments[3]));
    }
}
