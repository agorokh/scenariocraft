package io.github.agorokh.scenariocraft.renderer;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

public final class VoxelRenderer {
    public static final int IMAGE_SIZE = 1024;
    private static final int MARGIN = 56;
    private static final String[] OUTPUT_NAMES = {
        "iso-ne.png", "iso-se.png", "iso-sw.png", "iso-nw.png",
        "plan.png", "cut-x.png", "cut-z.png"
    };
    private static final Set<String> OUTPUT_NAME_SET = Set.of(OUTPUT_NAMES);

    private final BlockColors colors = new BlockColors();

    public void render(VoxelPlot plot, Path outputDirectory) throws IOException {
        prepareOutputDirectory(outputDirectory);
        write(renderIsometric(plot, 1, -1), outputDirectory.resolve(OUTPUT_NAMES[0]));
        write(renderIsometric(plot, 1, 1), outputDirectory.resolve(OUTPUT_NAMES[1]));
        write(renderIsometric(plot, -1, 1), outputDirectory.resolve(OUTPUT_NAMES[2]));
        write(renderIsometric(plot, -1, -1), outputDirectory.resolve(OUTPUT_NAMES[3]));
        write(renderPlan(plot), outputDirectory.resolve(OUTPUT_NAMES[4]));
        write(renderCutX(plot), outputDirectory.resolve(OUTPUT_NAMES[5]));
        write(renderCutZ(plot), outputDirectory.resolve(OUTPUT_NAMES[6]));
    }

    private void prepareOutputDirectory(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (var entries = Files.list(outputDirectory)) {
            Path unexpected = entries
                    .filter(path -> !OUTPUT_NAME_SET.contains(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
            if (unexpected != null) {
                throw new IOException("Output directory contains an unexpected entry: "
                        + unexpected.getFileName());
            }
        }
    }

    private BufferedImage renderIsometric(VoxelPlot plot, int xDirection, int zDirection) {
        BufferedImage image = transparentImage();
        if (plot.sizeX() == 0 || plot.sizeY() == 0 || plot.sizeZ() == 0) {
            return image;
        }

        List<Voxel> voxels = visibleVoxels(plot);
        voxels.sort(Comparator
                .comparingInt((Voxel voxel) -> xDirection * voxel.x + zDirection * voxel.z)
                .thenComparingInt(voxel -> voxel.y)
                .thenComparingInt(voxel -> voxel.x)
                .thenComparingInt(voxel -> voxel.z));

        double[] bounds = isoBounds(plot, xDirection, zDirection);
        Graphics2D graphics = graphics(image);
        applyFit(graphics, bounds);
        for (Voxel voxel : voxels) {
            Color base = colors.color(plot.blockIdAt(voxel.x, voxel.y, voxel.z));
            drawIsoVoxel(graphics, voxel, xDirection, zDirection, base);
        }
        graphics.dispose();
        return image;
    }

    private List<Voxel> visibleVoxels(VoxelPlot plot) {
        List<Voxel> result = new ArrayList<>();
        for (int y = 0; y < plot.sizeY(); y++) {
            for (int z = 0; z < plot.sizeZ(); z++) {
                for (int x = 0; x < plot.sizeX(); x++) {
                    if (plot.blockAt(x, y, z) != 0 && !fullyOccluded(plot, x, y, z)) {
                        result.add(new Voxel(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private boolean fullyOccluded(VoxelPlot plot, int x, int y, int z) {
        return plot.blockAt(x - 1, y, z) != 0 && plot.blockAt(x + 1, y, z) != 0
                && plot.blockAt(x, y - 1, z) != 0 && plot.blockAt(x, y + 1, z) != 0
                && plot.blockAt(x, y, z - 1) != 0 && plot.blockAt(x, y, z + 1) != 0;
    }

    private void drawIsoVoxel(Graphics2D graphics, Voxel voxel, int xd, int zd, Color base) {
        double x0 = voxel.x;
        double x1 = x0 + 1;
        double y0 = voxel.y;
        double y1 = y0 + 1;
        double z0 = voxel.z;
        double z1 = z0 + 1;

        graphics.setColor(shade(base, 1.12));
        graphics.fill(polygon(
                iso(x0, y1, z0, xd, zd), iso(x1, y1, z0, xd, zd),
                iso(x1, y1, z1, xd, zd), iso(x0, y1, z1, xd, zd)));

        double faceX = xd > 0 ? x1 : x0;
        graphics.setColor(shade(base, 0.82));
        graphics.fill(polygon(
                iso(faceX, y0, z0, xd, zd), iso(faceX, y0, z1, xd, zd),
                iso(faceX, y1, z1, xd, zd), iso(faceX, y1, z0, xd, zd)));

        double faceZ = zd > 0 ? z1 : z0;
        graphics.setColor(shade(base, 0.68));
        graphics.fill(polygon(
                iso(x0, y0, faceZ, xd, zd), iso(x1, y0, faceZ, xd, zd),
                iso(x1, y1, faceZ, xd, zd), iso(x0, y1, faceZ, xd, zd)));
    }

    private BufferedImage renderPlan(VoxelPlot plot) {
        BufferedImage image = transparentImage();
        Graphics2D graphics = graphics(image);
        applyGridFit(graphics, plot.sizeX(), plot.sizeZ());
        for (int z = 0; z < plot.sizeZ(); z++) {
            for (int x = 0; x < plot.sizeX(); x++) {
                for (int y = plot.sizeY() - 1; y >= 0; y--) {
                    if (plot.blockAt(x, y, z) != 0) {
                        fillGridCell(graphics, x, z, colors.color(plot.blockIdAt(x, y, z)));
                        break;
                    }
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderCutX(VoxelPlot plot) {
        BufferedImage image = transparentImage();
        Graphics2D graphics = graphics(image);
        applyGridFit(graphics, plot.sizeZ(), plot.sizeY());
        int x = plot.sizeX() / 2;
        for (int y = 0; y < plot.sizeY(); y++) {
            for (int z = 0; z < plot.sizeZ(); z++) {
                if (plot.blockAt(x, y, z) != 0) {
                    fillGridCell(graphics, z, plot.sizeY() - 1 - y,
                            colors.color(plot.blockIdAt(x, y, z)));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderCutZ(VoxelPlot plot) {
        BufferedImage image = transparentImage();
        Graphics2D graphics = graphics(image);
        applyGridFit(graphics, plot.sizeX(), plot.sizeY());
        int z = plot.sizeZ() / 2;
        for (int y = 0; y < plot.sizeY(); y++) {
            for (int x = 0; x < plot.sizeX(); x++) {
                if (plot.blockAt(x, y, z) != 0) {
                    fillGridCell(graphics, x, plot.sizeY() - 1 - y,
                            colors.color(plot.blockIdAt(x, y, z)));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private void fillGridCell(Graphics2D graphics, int column, int row, Color color) {
        graphics.setColor(color);
        graphics.fillRect(column, row, 1, 1);
    }

    private void applyGridFit(Graphics2D graphics, int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        double scale = Math.min((IMAGE_SIZE - 2.0 * MARGIN) / width,
                (IMAGE_SIZE - 2.0 * MARGIN) / height);
        double left = (IMAGE_SIZE - width * scale) / 2.0;
        double top = (IMAGE_SIZE - height * scale) / 2.0;
        graphics.translate(left, top);
        graphics.scale(scale, scale);
    }

    private void applyFit(Graphics2D graphics, double[] bounds) {
        double width = bounds[2] - bounds[0];
        double height = bounds[3] - bounds[1];
        double scale = Math.min((IMAGE_SIZE - 2.0 * MARGIN) / width,
                (IMAGE_SIZE - 2.0 * MARGIN) / height);
        double left = (IMAGE_SIZE - width * scale) / 2.0;
        double top = (IMAGE_SIZE - height * scale) / 2.0;
        graphics.translate(left, top);
        graphics.scale(scale, scale);
        graphics.translate(-bounds[0], -bounds[1]);
    }

    private double[] isoBounds(VoxelPlot plot, int xd, int zd) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int x : new int[] {0, plot.sizeX()}) {
            for (int y : new int[] {0, plot.sizeY()}) {
                for (int z : new int[] {0, plot.sizeZ()}) {
                    double[] point = iso(x, y, z, xd, zd);
                    minX = Math.min(minX, point[0]);
                    minY = Math.min(minY, point[1]);
                    maxX = Math.max(maxX, point[0]);
                    maxY = Math.max(maxY, point[1]);
                }
            }
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    private double[] iso(double x, double y, double z, int xd, int zd) {
        return new double[] {xd * x - zd * z, (xd * x + zd * z) * 0.5 - y};
    }

    private Path2D polygon(double[]... points) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0][0], points[0][1]);
        for (int index = 1; index < points.length; index++) {
            path.lineTo(points[index][0], points[index][1]);
        }
        path.closePath();
        return path;
    }

    private Color shade(Color color, double multiplier) {
        return new Color(
                Math.min(255, (int) Math.round(color.getRed() * multiplier)),
                Math.min(255, (int) Math.round(color.getGreen() * multiplier)),
                Math.min(255, (int) Math.round(color.getBlue() * multiplier)),
                color.getAlpha());
    }

    private BufferedImage transparentImage() {
        return new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.setTransform(new AffineTransform());
        return graphics;
    }

    private void write(BufferedImage image, Path output) throws IOException {
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }

    private record Voxel(int x, int y, int z) {}
}
