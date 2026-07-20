package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Stable hub-relative position for the cosmetic secret-note chest. */
public record SecretChestPosition(int x, int y, int z) {
    private static final int HUB_OFFSET_X = 2;

    public static SecretChestPosition atHub(ArenaWorld arena) {
        Location spawn = arena.world().getSpawnLocation();
        return new SecretChestPosition(
                Math.addExact(spawn.getBlockX(), HUB_OFFSET_X),
                Math.addExact(arena.floorY(), 1),
                spawn.getBlockZ());
    }

    public boolean matches(Block block) {
        return block.getX() == x && block.getY() == y && block.getZ() == z;
    }

    Cuboid asCuboid() {
        return new Cuboid(x, x, y, y, z, z);
    }
}
