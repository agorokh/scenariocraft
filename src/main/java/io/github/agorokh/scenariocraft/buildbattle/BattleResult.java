package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded, display-safe view of one judge-produced {@code results.txt}. */
record BattleResult(
        String roundId,
        String task,
        List<Contestant> contestants,
        Optional<Winner> winner) {
    BattleResult {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(task, "task");
        contestants = List.copyOf(contestants);
        winner = Objects.requireNonNull(winner, "winner");
    }

    record Contestant(String player, String plotId, List<Feedback> feedback) {
        Contestant {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(plotId, "plotId");
            feedback = List.copyOf(feedback);
        }
    }

    record Feedback(String persona, String comment) {
        Feedback {
            Objects.requireNonNull(persona, "persona");
            Objects.requireNonNull(comment, "comment");
        }
    }

    record Winner(String player, String plotId) {
        Winner {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(plotId, "plotId");
        }
    }
}
