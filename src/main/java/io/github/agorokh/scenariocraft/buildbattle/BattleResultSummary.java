package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;

/** Parsed, player-facing subset of one judge results.txt artifact. */
record BattleResultSummary(
        String roundId,
        String task,
        List<ContestantFeedback> contestants,
        Winner winner,
        String noWinnerReason) {
    BattleResultSummary {
        contestants = List.copyOf(contestants);
    }

    boolean hasWinner() {
        return winner != null;
    }

    record ContestantFeedback(
            String plotId, String player, List<PersonaFeedback> feedback) {
        ContestantFeedback {
            feedback = List.copyOf(feedback);
        }
    }

    record PersonaFeedback(String persona, String score, String comment) {}

    record Winner(String plotId, String player) {}
}
