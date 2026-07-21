package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stateful configured task deck that uses every unique prompt before repeating one. */
final class TaskDeck {
    private final List<String> tasks;
    private final IntUnaryOperator randomIndex;
    private final Path historyFile;
    private final Logger logger;
    private final Executor historyExecutor;
    private final Set<String> recentPicks = new LinkedHashSet<>();
    private CompletableFuture<Void> pendingWrite = CompletableFuture.completedFuture(null);

    TaskDeck(List<String> tasks, IntUnaryOperator randomIndex) {
        this(tasks, randomIndex, null, null);
    }

    TaskDeck(
            List<String> tasks,
            IntUnaryOperator randomIndex,
            Path historyFile,
            Logger logger) {
        this(tasks, randomIndex, historyFile, logger, Runnable::run);
    }

    TaskDeck(
            List<String> tasks,
            IntUnaryOperator randomIndex,
            Path historyFile,
            Logger logger,
            Executor historyExecutor) {
        this.tasks = List.copyOf(new LinkedHashSet<>(tasks));
        if (this.tasks.isEmpty()) {
            throw new IllegalArgumentException("task deck must not be empty");
        }
        this.randomIndex = Objects.requireNonNull(randomIndex, "randomIndex");
        this.historyFile = historyFile;
        this.logger = logger;
        this.historyExecutor = Objects.requireNonNull(historyExecutor, "historyExecutor");
        loadHistory();
    }

    String draw() {
        if (recentPicks.size() == tasks.size()) {
            recentPicks.clear();
        }
        List<String> available =
                tasks.stream().filter(task -> !recentPicks.contains(task)).toList();
        int selectedIndex = randomIndex.applyAsInt(available.size());
        if (selectedIndex < 0 || selectedIndex >= available.size()) {
            throw new IllegalStateException("random task index was outside the remaining deck");
        }
        String selected = available.get(selectedIndex);
        recentPicks.add(selected);
        saveHistory();
        return selected;
    }

    private void loadHistory() {
        if (historyFile == null || !Files.isRegularFile(historyFile)) {
            return;
        }
        try {
            Files.readAllLines(historyFile, StandardCharsets.UTF_8).stream()
                    .filter(tasks::contains)
                    .forEach(recentPicks::add);
        } catch (IOException failure) {
            logHistoryFailure("read", failure);
        }
    }

    private synchronized void saveHistory() {
        if (historyFile == null) {
            return;
        }
        List<String> snapshot = List.copyOf(recentPicks);
        try {
            pendingWrite =
                    pendingWrite
                            .handle((ignored, failure) -> null)
                            .thenRunAsync(() -> writeHistory(snapshot), historyExecutor);
        } catch (RuntimeException failure) {
            logHistoryFailure("schedule a write for", failure);
        }
    }

    private void writeHistory(List<String> snapshot) {
        Path parent = historyFile.toAbsolutePath().normalize().getParent();
        Path temporary = parent.resolve(historyFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.write(temporary, snapshot, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        historyFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            logHistoryFailure("write", failure);
        }
    }

    private void logHistoryFailure(String operation, Exception failure) {
        if (logger != null) {
            logger.log(
                    Level.WARNING,
                    "Could not "
                            + operation
                            + " Speed Build task history; continuing with the in-memory deck",
                    failure);
        }
    }
}
