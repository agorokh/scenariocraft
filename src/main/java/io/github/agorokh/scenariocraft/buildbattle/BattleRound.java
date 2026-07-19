package io.github.agorokh.scenariocraft.buildbattle;

import org.bukkit.command.CommandSender;

/** Command-facing round operations, separated so authorization stays unit-testable. */
public interface BattleRound {
    RoundPhase phase();

    void start(CommandSender sender);

    void stop(CommandSender sender);
}
