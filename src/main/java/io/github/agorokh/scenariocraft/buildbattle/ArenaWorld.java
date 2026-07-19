package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.World;

/** Loaded arena world and its spawn-floor height. */
public record ArenaWorld(World world, int floorY) {}
