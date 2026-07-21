package io.github.agorokh.scenariocraft.buildbattle;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

/** Owns the exact console-command path used for contestant relocation. */
public final class TeleportTransport {
    private static final Pattern COMMAND_WORLD_KEY =
            Pattern.compile("[a-z0-9._-]+:[a-z0-9/._-]+");

    private final Server server;

    public TeleportTransport(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /** Returns whether both exact namespaced commands used by dispatch are registered. */
    public boolean isAvailable() {
        return commandsAvailable(server.getCommandMap());
    }

    /** Dispatches the production command shape from the console. */
    public boolean dispatch(Player player, Location destination) {
        Objects.requireNonNull(player, "player");
        return server.dispatchCommand(
                server.getConsoleSender(), command(player, destination));
    }

    /** Exercises the production command path without changing the player's location. */
    public boolean probe(Player player) {
        return isAvailable() && dispatch(player, player.getLocation());
    }

    public static boolean commandsAvailable(CommandMap commandMap) {
        return commandMap != null
                && commandMap.getCommand("minecraft:execute") != null
                && commandMap.getCommand("minecraft:tp") != null;
    }

    static String command(Player player, Location destination) {
        World world =
                Objects.requireNonNull(
                        destination.getWorld(), "teleport destination world");
        String worldKey = world.getKey().toString();
        if (!COMMAND_WORLD_KEY.matcher(worldKey).matches()) {
            throw new IllegalArgumentException(
                    "invalid teleport destination world key: " + worldKey);
        }
        return "minecraft:execute in "
                + worldKey
                + " run minecraft:tp "
                + player.getUniqueId()
                + " "
                + commandNumber(destination.getX())
                + " "
                + commandNumber(destination.getY())
                + " "
                + commandNumber(destination.getZ())
                + " "
                + commandNumber(destination.getYaw())
                + " "
                + commandNumber(destination.getPitch());
    }

    private static String commandNumber(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("teleport coordinates must be finite");
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String commandNumber(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("teleport rotation must be finite");
        }
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
}
