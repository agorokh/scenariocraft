package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
}
