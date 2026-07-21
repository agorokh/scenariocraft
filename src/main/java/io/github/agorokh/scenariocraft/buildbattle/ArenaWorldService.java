package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/** Creates or loads the fixed Speed Build world and applies its stable world rules. */
public final class ArenaWorldService {
    public static final String WORLD_NAME = "battle_world";
    private static final long DAYLIGHT_TICKS = 6_000L;

    private final Server server;
    private final Logger logger;

    public ArenaWorldService(Server server, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ArenaWorld loadOrCreate() {
        World world = server.getWorld(WORLD_NAME);
        if (world == null) {
            WorldCreator creator =
                    new WorldCreator(WORLD_NAME)
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.FLAT)
                            .generateStructures(false);
            world =
                    Objects.requireNonNull(
                            server.createWorld(creator), "Paper could not create battle_world");
        }
        requireFlat(world.getWorldType());
        logger.info("Loaded or created superflat world battle_world.");

        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setTime(DAYLIGHT_TICKS);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnFlags(false, false);

        Location spawn = world.getSpawnLocation();
        Chunk spawnChunk = world.getChunkAt(spawn);
        if (!spawnChunk.load(true)) {
            throw new IllegalStateException("Paper could not load battle_world's spawn chunk");
        }
        int floorY =
                world.getHighestBlockYAt(
                        spawn.getBlockX(),
                        spawn.getBlockZ(),
                        HeightMap.MOTION_BLOCKING_NO_LEAVES);
        logger.info(
                "Loaded battle_world spawn chunk before reading arena floor at hub x="
                        + spawn.getBlockX()
                        + ", z="
                        + spawn.getBlockZ()
                        + ", Y="
                        + floorY
                        + ".");
        return new ArenaWorld(world, floorY);
    }

    static void requireFlat(WorldType worldType) {
        if (worldType != WorldType.FLAT) {
            throw new IllegalStateException(
                    "Existing battle_world is not superflat; move or replace it before enabling ScenarioCraft");
        }
    }
}
