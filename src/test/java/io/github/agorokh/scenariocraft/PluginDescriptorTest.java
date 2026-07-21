package io.github.agorokh.scenariocraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

class PluginDescriptorTest {
    @Test
    void descriptorDeclaresEntryPointAndSpeedBuildCommandWithLegacyAliases() throws IOException {
        try (InputStream descriptor =
                PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(descriptor, "plugin.yml must be packaged as a resource");
            String yaml = new String(descriptor.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(yaml.contains("name: ScenarioCraft"));
            assertTrue(
                    yaml.contains(
                            "main: io.github.agorokh.scenariocraft.ScenarioCraftPlugin"));
            assertTrue(yaml.contains("api-version: '1.21.11'"));
            assertTrue(yaml.contains("  speedbuild:"));
            assertTrue(yaml.contains("      - battle"));
            assertTrue(yaml.contains("      - buildbattle"));
            assertTrue(yaml.contains("      - bb"));
            assertTrue(yaml.contains("  scenariocraft.alerts:"));
        }
    }

    @Test
    void entryPointUsesThePaperLifecycle() {
        assertEquals(JavaPlugin.class, ScenarioCraftPlugin.class.getSuperclass());
        assertTrue(
                Arrays.stream(ScenarioCraftPlugin.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("onEnable")));
        assertTrue(
                Arrays.stream(ScenarioCraftPlugin.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("onDisable")));
    }
}
