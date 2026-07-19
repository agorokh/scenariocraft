package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionPluginWarnerTest {
    @Test
    void recognizesKnownAndDescriptiveProtectionPluginNames() {
        assertTrue(ProtectionPluginWarner.mayProtectLand("WorldGuard"));
        assertTrue(ProtectionPluginWarner.mayProtectLand("Grief-Prevention"));
        assertTrue(ProtectionPluginWarner.mayProtectLand("FriendlyClaims"));
        assertFalse(ProtectionPluginWarner.mayProtectLand("ScenarioCraft"));
    }
}
