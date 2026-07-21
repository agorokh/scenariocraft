package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TeleportTransportTest {
    @Test
    void productionCommandIsNamespacedExplicitWorldAndLocaleIndependent() {
        World world =
                proxy(
                        World.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getKey")
                                        ? new NamespacedKey("minecraft", "battle_world")
                                        : defaultValue(method.getReturnType()));
        UUID playerId = UUID.fromString("9a49fbc6-1d0b-4b12-a37b-cbb1b0f6d5cc");
        Player player =
                proxy(
                        Player.class,
                        (ignored, method, arguments) ->
                                method.getName().equals("getUniqueId")
                                        ? playerId
                                        : defaultValue(method.getReturnType()));

        assertEquals(
                "minecraft:execute in minecraft:battle_world run minecraft:tp "
                        + playerId
                        + " 1.5 -2 3.25 90 0",
                TeleportTransport.command(
                        player, new Location(world, 1.5, -2.0, 3.25, 90.0F, 0.0F)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
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
