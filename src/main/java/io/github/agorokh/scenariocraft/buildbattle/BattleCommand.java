package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/** Bedrock-compatible text command for starting and stopping Build Battle rounds. */
public final class BattleCommand implements CommandExecutor {
    private final BattleSettings settings;
    private final BattleRound round;
    private final BattleResultCommands results;

    public BattleCommand(
            BattleSettings settings, BattleRound round, BattleResultCommands results) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.round = Objects.requireNonNull(round, "round");
        this.results = Objects.requireNonNull(results, "results");
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("announce")) {
            if (!(sender instanceof ConsoleCommandSender)
                    && !(sender instanceof RemoteConsoleCommandSender)) {
                sender.sendMessage("Only the server console can announce a judged round.");
                return true;
            }
            results.announceRound(arguments[1], sender);
            return true;
        }
        if (arguments.length != 1) {
            usage(sender, label);
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
        if (arguments[0].equalsIgnoreCase("results")) {
            results.showLatest(sender);
            return true;
        }
        usage(sender, label);
        return true;
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage("Try /" + label + " start, /" + label + " stop, or /" + label + " results.");
    }
}
