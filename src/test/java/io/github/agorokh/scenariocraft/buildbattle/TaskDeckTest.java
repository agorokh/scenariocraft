package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDeckTest {
    @TempDir Path temporaryDirectory;

    @Test
    void noTaskRepeatsUntilEveryConfiguredTaskHasBeenDrawn() {
        List<String> configured = List.of("Castle", "Rocket", "Bakery");
        TaskDeck deck = new TaskDeck(configured, bound -> bound - 1);

        Set<String> firstCycle =
                new LinkedHashSet<>(List.of(deck.draw(), deck.draw(), deck.draw()));

        assertEquals(Set.copyOf(configured), firstCycle);
        assertEquals(3, firstCycle.size());
        assertEquals("Bakery", deck.draw());
    }

    @Test
    void duplicateConfiguredPromptsDoNotCreateAnEarlyRepeat() {
        TaskDeck deck = new TaskDeck(List.of("Castle", "Castle", "Rocket"), ignored -> 0);

        String first = deck.draw();
        String second = deck.draw();

        assertNotEquals(first, second);
    }

    @Test
    void persistedHistoryPreventsImmediateRepeatAfterRestart() {
        Path history = temporaryDirectory.resolve("task-history.txt");
        List<String> tasks = List.of("Castle", "Rocket", "Bakery");

        TaskDeck firstServer = new TaskDeck(tasks, ignored -> 0, history, null);
        assertEquals("Castle", firstServer.draw());

        TaskDeck restartedServer = new TaskDeck(tasks, ignored -> 0, history, null);
        assertEquals("Rocket", restartedServer.draw());
    }

    @Test
    void historyWriteRunsOnConfiguredExecutor() {
        Path history = temporaryDirectory.resolve("async-task-history.txt");
        List<Runnable> queuedWrites = new ArrayList<>();
        TaskDeck deck =
                new TaskDeck(
                        List.of("Castle", "Rocket"),
                        ignored -> 0,
                        history,
                        null,
                        queuedWrites::add);

        assertEquals("Castle", deck.draw());
        assertFalse(history.toFile().exists());
        assertEquals(1, queuedWrites.size());

        queuedWrites.removeFirst().run();

        TaskDeck restarted =
                new TaskDeck(List.of("Castle", "Rocket"), ignored -> 0, history, null);
        assertEquals("Rocket", restarted.draw());
    }

    @Test
    void flushWaitsForPendingHistoryWrite() {
        Path history = temporaryDirectory.resolve("flushed-task-history.txt");
        try (ExecutorService writer = Executors.newSingleThreadExecutor()) {
            TaskDeck deck =
                    new TaskDeck(
                            List.of("Castle", "Rocket"),
                            ignored -> 0,
                            history,
                            null,
                            writer);

            assertEquals("Castle", deck.draw());
            deck.flushHistory();

            TaskDeck restarted =
                    new TaskDeck(List.of("Castle", "Rocket"), ignored -> 0, history, null);
            assertEquals("Rocket", restarted.draw());
        }
    }

    @Test
    void concurrentDrawsAndAsyncSnapshotsDoNotCorruptHistory() throws Exception {
        Path history = temporaryDirectory.resolve("concurrent-task-history.txt");
        List<String> tasks = List.of("Castle", "Rocket", "Bakery", "Dragon");
        try (ExecutorService writers = Executors.newFixedThreadPool(2);
                ExecutorService callers = Executors.newFixedThreadPool(4)) {
            TaskDeck deck = new TaskDeck(tasks, ignored -> 0, history, null, writers);
            List<Future<?>> draws = new ArrayList<>();
            for (int index = 0; index < 99; index++) {
                draws.add(callers.submit(deck::draw));
            }
            for (Future<?> draw : draws) {
                draw.get();
            }
            deck.flushHistory();

            TaskDeck restarted = new TaskDeck(tasks, ignored -> 0, history, null);
            assertFalse(tasks.subList(0, 3).contains(restarted.draw()));
        }
    }
}
