package io.github.agorokh.scenariocraft.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class BlockColorsTest {
    @Test
    void proofRoundMaterialsUseExplicitFaithfulColors() {
        BlockColors colors = new BlockColors();

        assertEquals(Color.decode("#5EA818"), colors.color("minecraft:lime_concrete"));
        assertEquals(Color.decode("#A9309F"), colors.color("minecraft:magenta_concrete"));
        assertEquals(Color.decode("#F9D849"), colors.color("minecraft:gold_block"));
    }
}
