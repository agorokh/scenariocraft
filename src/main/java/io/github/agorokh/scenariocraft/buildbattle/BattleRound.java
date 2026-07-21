package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Optional;
import org.bukkit.command.CommandSender;

/** Command-facing round operations, separated so authorization stays unit-testable. */
public interface BattleRound {
    RoundPhase phase();

    /** Identifies the export that may produce results for the active round. */
    default Optional<String> activeResultRoundId() {
        return Optional.empty();
    }

    void start(CommandSender sender);

    void stop(CommandSender sender);
}
