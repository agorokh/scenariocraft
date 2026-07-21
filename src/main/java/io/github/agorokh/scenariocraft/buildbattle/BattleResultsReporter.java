package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.command.CommandSender;

/** Command-facing boundary for replaying or broadcasting durable judge results. */
public interface BattleResultsReporter {
    void replayLatest(CommandSender sender);

    void announceLatest(CommandSender sender);
}
