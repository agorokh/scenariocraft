package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecordedResultValidatorTest {
    private static final JudgeConfig CONFIG = new JudgeConfig(
            2,
            List.of(
                    new Persona("Professor Brickworth", "Precise."),
                    new Persona("Captain Sparkle", "Bold."),
                    new Persona("Granny Redstone", "Warm.")),
            "theme_fit creativity effort detail");

    @Test
    void acceptsAProductionValidConfiguredPanel() {
        assertDoesNotThrow(() -> RecordedResultValidator.validate(
                results(validVerdicts()), "Build a cabin", CONFIG));
    }

    @Test
    void rejectsCruelTextThroughTheProductionVerdictContract() {
        assertThrows(IllegalArgumentException.class, () -> new JudgeVerdict(
                "Professor Brickworth",
                "The clear build space is stupid.",
                new Scores(5, 5, 5, 5),
                "Your build space is clear. Add a doorway next."));
    }

    @Test
    void rejectsAIncompletePanelOrMismatchedMean() {
        List<JudgeVerdict> verdicts = validVerdicts();
        assertThrows(IllegalArgumentException.class, () -> RecordedResultValidator.validate(
                results(verdicts.subList(0, 2)), "Build a cabin", CONFIG));
        RoundResults mismatched = new RoundResults(
                1,
                "round-20260721-120000",
                "Build a cabin",
                List.of(new RoundResults.ContestantResult(
                        "p1", "Eval builder", verdicts, 10.0, List.of())),
                new RoundResults.Winner("p1", "Eval builder", 10.0),
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> RecordedResultValidator.validate(
                mismatched, "Build a cabin", CONFIG));
    }

    private static RoundResults results(List<JudgeVerdict> verdicts) {
        double mean = verdicts.stream().mapToDouble(JudgeVerdict::score).average().orElseThrow();
        return new RoundResults(
                1,
                "round-20260721-120000",
                "Build a cabin",
                List.of(new RoundResults.ContestantResult(
                        "p1", "Eval builder", verdicts, mean, List.of())),
                new RoundResults.Winner("p1", "Eval builder", mean),
                null,
                null);
    }

    private static List<JudgeVerdict> validVerdicts() {
        return List.of(
                verdict("Professor Brickworth"),
                verdict("Captain Sparkle"),
                verdict("Granny Redstone"));
    }

    private static JudgeVerdict verdict(String persona) {
        return new JudgeVerdict(
                persona,
                "The doorway and roof make the cabin task clear.",
                new Scores(7, 7, 7, 7),
                "Your doorway has a clear welcoming shape. Add roof texture next.");
    }
}
