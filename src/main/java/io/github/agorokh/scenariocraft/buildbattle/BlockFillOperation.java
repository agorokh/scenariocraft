package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.Material;
import org.bukkit.World;

/** Lazily fills one cuboid, one block mutation at a time. */
final class BlockFillOperation implements IncrementalWork {
    private final World world;
    private final Cuboid bounds;
    private final Material material;
    private int x;
    private int y;
    private int z;
    private long remaining;

    BlockFillOperation(World world, Cuboid bounds, Material material) {
        this.world = world;
        this.bounds = bounds;
        this.material = material;
        x = bounds.minX();
        y = bounds.minY();
        z = bounds.minZ();
        remaining = bounds.blockCount();
    }

    @Override
    public boolean hasNext() {
        return remaining > 0;
    }

    @Override
    public void runNext() {
        if (!hasNext()) {
            throw new IllegalStateException("fill operation is already complete");
        }
        world.getBlockAt(x, y, z).setType(material, false);
        remaining--;
        advance();
    }

    @Override
    public long remaining() {
        return remaining;
    }

    private void advance() {
        if (x < bounds.maxX()) {
            x++;
            return;
        }
        x = bounds.minX();
        if (z < bounds.maxZ()) {
            z++;
            return;
        }
        z = bounds.minZ();
        if (y < bounds.maxY()) {
            y++;
        }
    }
}
