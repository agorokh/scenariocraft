package io.github.agorokh.scenariocraft.judge;

import com.google.gson.annotations.SerializedName;
import java.util.List;

record RoundResults(
        int schema,
        @SerializedName("round_id") String roundId,
        String task,
        List<ContestantResult> contestants,
        Winner winner,
        @SerializedName("no_winner") Boolean noWinner,
        String reason) {
    RoundResults {
        contestants = List.copyOf(contestants);
    }

    boolean hasWinner() {
        return winner != null;
    }

    record ContestantResult(
            @SerializedName("plot_id") String plotId,
            String player,
            List<JudgeVerdict> verdicts,
            Double mean,
            List<String> failures) {
        ContestantResult {
            verdicts = List.copyOf(verdicts);
            failures = List.copyOf(failures);
        }
    }

    record Winner(
            @SerializedName("plot_id") String plotId,
            String player,
            double mean) {}
}
