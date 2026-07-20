package io.github.agorokh.scenariocraft.buildbattle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Stateful configured task deck that uses every unique prompt before repeating one. */
final class TaskDeck {
    private final List<String> tasks;
    private final IntUnaryOperator randomIndex;
    private final Set<String> recentPicks = new LinkedHashSet<>();

    TaskDeck(List<String> tasks, IntUnaryOperator randomIndex) {
        this.tasks = List.copyOf(new LinkedHashSet<>(tasks));
        if (this.tasks.isEmpty()) {
            throw new IllegalArgumentException("task deck must not be empty");
        }
        this.randomIndex = Objects.requireNonNull(randomIndex, "randomIndex");
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
        return selected;
    }
}
