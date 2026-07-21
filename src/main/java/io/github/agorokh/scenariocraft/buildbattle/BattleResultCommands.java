package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.command.CommandSender;

/** Command-facing result operations, separated from round lifecycle authorization. */
public interface BattleResultCommands {
    void showLatest(CommandSender sender);

    void announceRound(String roundId, CommandSender sender);
}
