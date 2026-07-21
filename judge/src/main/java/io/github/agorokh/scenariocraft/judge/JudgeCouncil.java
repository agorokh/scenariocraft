package io.github.agorokh.scenariocraft.judge;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class JudgeCouncil {
    private JudgeCouncil() {}

    static RoundResults judge(
            JudgeRound round,
            JudgeConfig config,
            Map<String, List<Path>> images,
            PersonaJudge judge,
            PrintWriter diagnostics) throws JudgeException {
        List<RoundResults.ContestantResult> contestants = new ArrayList<>();
        String quorumFailure = null;
        for (JudgeRound.Plot plot : round.plots()) {
            List<JudgeVerdict> verdicts = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (Persona persona : config.personas()) {
                JudgeVerdict verdict = attemptTwice(
                        judge, persona, round, config.rubric(), plot, images.get(plot.plotId()),
                        failures, diagnostics);
                if (verdict != null) {
                    verdicts.add(verdict);
                }
            }
            Double mean = verdicts.isEmpty()
                    ? null
                    : verdicts.stream().mapToDouble(JudgeVerdict::score).average().orElseThrow();
            contestants.add(new RoundResults.ContestantResult(
                    plot.plotId(), plot.player(), verdicts, mean, failures));
            if (verdicts.size() < config.minJudges() && quorumFailure == null) {
                quorumFailure = "No winner: " + plot.player() + " received " + verdicts.size()
                        + " successful verdict" + (verdicts.size() == 1 ? "" : "s")
                        + "; at least " + config.minJudges() + " are required.";
            }
        }
        if (quorumFailure != null) {
            return new RoundResults(
                    1, round.roundId(), round.task(), contestants,
                    null, true, quorumFailure);
        }
        int expectedVerdictCount = contestants.getFirst().verdicts().size();
        if (contestants.stream()
                .anyMatch(contestant -> contestant.verdicts().size() != expectedVerdictCount)) {
            String reason = "No winner: contestants received unequal successful verdict counts.";
            return new RoundResults(
                    1, round.roundId(), round.task(), contestants,
                    null, true, reason);
        }
        List<RoundResults.ContestantResult> ranked = contestants.stream()
                .sorted(Comparator.comparingDouble(
                        (RoundResults.ContestantResult result) -> result.mean()).reversed())
                .toList();
        if (ranked.size() > 1
                && Double.compare(ranked.get(0).mean(), ranked.get(1).mean()) == 0) {
            String reason = "No winner: the top contestant means are tied.";
            return new RoundResults(
                    1, round.roundId(), round.task(), contestants,
                    null, true, reason);
        }
        RoundResults.ContestantResult winner = ranked.getFirst();
        return new RoundResults(
                1, round.roundId(), round.task(), contestants,
                new RoundResults.Winner(winner.plotId(), winner.player(), winner.mean()),
                null, null);
    }

    private static JudgeVerdict attemptTwice(
            PersonaJudge judge,
            Persona persona,
            JudgeRound round,
            String rubric,
            JudgeRound.Plot plot,
            List<Path> images,
            List<String> failures,
            PrintWriter diagnostics) throws JudgeException {
        String lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return judge.judge(persona, round.task(), rubric, plot.plotId(), images);
            } catch (JudgeException exception) {
                if (!exception.retryable()) {
                    throw exception;
                }
                lastFailure = persona.name() + ": " + exception.getMessage();
                diagnostics.println("Judge " + persona.name() + " attempt " + attempt
                        + " failed for " + plot.player() + ": " + exception.getMessage());
            }
        }
        failures.add(lastFailure);
        return null;
    }
}
