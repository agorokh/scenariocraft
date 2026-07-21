package io.github.agorokh.scenariocraft.judge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class ResultsWriter {
    private static final Gson JSON =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ResultsWriter() {}

    static void write(Path roundDirectory, RoundResults results) throws IOException {
        Path json = roundDirectory.resolve("results.json");
        Path text = roundDirectory.resolve("results.txt");
        try {
            writeAtomically(json, JSON.toJson(results) + System.lineSeparator());
            writeAtomically(text, humanReadable(results));
        } catch (IOException primaryFailure) {
            deleteAfterFailedPublication(json, primaryFailure);
            deleteAfterFailedPublication(text, primaryFailure);
            throw primaryFailure;
        }
    }

    static String humanReadable(RoundResults results) {
        StringBuilder output = new StringBuilder();
        output.append("Round: ").append(results.roundId()).append(System.lineSeparator());
        output.append("Task: ").append(results.task()).append(System.lineSeparator());
        output.append(System.lineSeparator());
        for (RoundResults.ContestantResult contestant : results.contestants()) {
            output.append(contestant.player()).append(" (").append(contestant.plotId())
                    .append(")").append(System.lineSeparator());
            for (JudgeVerdict verdict : contestant.verdicts()) {
                output.append("  ").append(verdict.persona()).append(": ")
                        .append(String.format(Locale.ROOT, "%.2f", verdict.score()))
                        .append(" — ").append(verdict.comment()).append(System.lineSeparator());
            }
            if (contestant.mean() != null) {
                output.append("  Mean: ")
                        .append(String.format(Locale.ROOT, "%.2f", contestant.mean()))
                        .append(System.lineSeparator());
            }
            for (String failure : contestant.failures()) {
                output.append("  Failed: ").append(failure).append(System.lineSeparator());
            }
            output.append(System.lineSeparator());
        }
        if (results.hasWinner()) {
            output.append("Winner: ").append(results.winner().player()).append(" with ")
                    .append(String.format(Locale.ROOT, "%.2f", results.winner().mean()))
                    .append(System.lineSeparator());
        } else {
            output.append(results.reason()).append(System.lineSeparator());
        }
        return output.toString();
    }

    private static void writeAtomically(Path target, String contents) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void deleteAfterFailedPublication(Path target, IOException primaryFailure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }
}
