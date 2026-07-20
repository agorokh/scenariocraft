package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Bedrock-compatible text command for starting and stopping Build Battle rounds. */
public final class BattleCommand implements CommandExecutor {
    private final BattleSettings settings;
    private final BattleRound round;

    public BattleCommand(BattleSettings settings, BattleRound round) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.round = Objects.requireNonNull(round, "round");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length != 1) {
            sender.sendMessage("Try /" + label + " start or /" + label + " stop.");
            return true;
        }
        if (arguments[0].equalsIgnoreCase("start")) {
            if (!settings.canStart(sender.getName(), sender.isOp())) {
                sender.sendMessage("A grown-up helper needs to start this battle.");
                return true;
            }
            round.start(sender);
            return true;
        }
        if (arguments[0].equalsIgnoreCase("stop")) {
            if (!sender.isOp()) {
                sender.sendMessage("Only a grown-up helper can stop the battle.");
                return true;
            }
            round.stop(sender);
            return true;
        }
        sender.sendMessage("Try /" + label + " start or /" + label + " stop.");
        return true;
    }
}
