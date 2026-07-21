package io.github.agorokh.scenariocraft.judge;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

final class RecordedResultValidator {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TASK_BYTES = 64 * 1024;
    private static final Gson JSON = new Gson();

    private RecordedResultValidator() {}

    static int run(
            Path responsePath,
            Path taskPath,
            Path personasPath,
            Path rubricPath,
            PrintWriter diagnostics) {
        try {
            String response = readBounded(responsePath, MAX_RESPONSE_BYTES);
            String task = readBounded(taskPath, MAX_TASK_BYTES).strip();
            JudgeConfig config = JudgeConfig.load(personasPath, rubricPath);
            RoundResults results = JSON.fromJson(response, RoundResults.class);
            validate(results, task, config);
            return 0;
        } catch (IOException | RuntimeException exception) {
            diagnostics.println("Recorded response failed production validation: "
                    + safeMessage(exception));
            diagnostics.flush();
            return 1;
        }
    }

    static void validate(RoundResults results, String expectedTask, JudgeConfig config) {
        if (results == null || results.schema() != 1 || !expectedTask.equals(results.task())) {
            throw new IllegalArgumentException("schema or task does not match the eval case");
        }
        if (results.contestants().size() != 1 || !results.hasWinner()
                || results.noWinner() != null || results.reason() != null) {
            throw new IllegalArgumentException(
                    "a recorded eval must contain one successful contestant");
        }
        RoundResults.ContestantResult contestant = results.contestants().getFirst();
        List<String> expectedPanel = config.personas().stream().map(Persona::name).toList();
        List<String> actualPanel = contestant.verdicts().stream()
                .map(JudgeVerdict::persona)
                .toList();
        if (!actualPanel.equals(expectedPanel) || !contestant.failures().isEmpty()) {
            throw new IllegalArgumentException(
                    "recorded verdicts must match the configured successful panel");
        }
        double computedMean = contestant.verdicts().stream()
                .mapToDouble(JudgeVerdict::score)
                .average()
                .orElseThrow();
        if (contestant.mean() == null
                || !Double.isFinite(contestant.mean())
                || Double.compare(contestant.mean(), computedMean) != 0) {
            throw new IllegalArgumentException("contestant mean does not match verdicts");
        }
        RoundResults.Winner winner = results.winner();
        if (!winner.plotId().equals(contestant.plotId())
                || !winner.player().equals(contestant.player())
                || !Double.isFinite(winner.mean())
                || Double.compare(winner.mean(), computedMean) != 0) {
            throw new IllegalArgumentException("winner does not match the scored contestant");
        }
    }

    private static String readBounded(Path path, int maximum) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(maximum + 1);
        }
        if (bytes.length == 0 || bytes.length > maximum) {
            throw new IOException(path.getFileName() + " size is outside the allowed range");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank() || message.length() > 240
                || message.codePoints().anyMatch(Character::isISOControl)) {
            return "invalid recorded result";
        }
        return message;
    }
}
