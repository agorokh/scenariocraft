package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;
import org.bukkit.Material;

/** One material fill in an arena build plan. */
public record BlockFill(Cuboid bounds, Material material) {
    public BlockFill {
        bounds = Objects.requireNonNull(bounds, "bounds");
        material = Objects.requireNonNull(material, "material");
    }
}
