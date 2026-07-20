package io.github.agorokh.scenariocraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class ScenarioCraftPluginTest {
    @Test
    void teleportReadinessRequiresTheExactNamespacedCommands() {
        assertTrue(
                ScenarioCraftPlugin.requiredTeleportCommandsAvailable(
                        commandMapWith(
                                "minecraft:execute",
                                "minecraft:tp")));
        assertFalse(
                ScenarioCraftPlugin.requiredTeleportCommandsAvailable(
                        commandMapWith("execute", "tp")));
        assertFalse(
                ScenarioCraftPlugin.requiredTeleportCommandsAvailable(
                        commandMapWith("minecraft:execute", "tp")));
    }

    private static CommandMap commandMapWith(String... commands) {
        Set<String> available = Set.of(commands);
        Command marker =
                new Command("marker") {
                    @Override
                    public boolean execute(
                            CommandSender sender,
                            String label,
                            String[] arguments) {
                        return true;
                    }
                };
        return (CommandMap)
                Proxy.newProxyInstance(
                        CommandMap.class.getClassLoader(),
                        new Class<?>[] {CommandMap.class},
                        (ignored, method, arguments) ->
                                method.getName().equals("getCommand")
                                                && available.contains(arguments[0])
                                        ? marker
                                        : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
