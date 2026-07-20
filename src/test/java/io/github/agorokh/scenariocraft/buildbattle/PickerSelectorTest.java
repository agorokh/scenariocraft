package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PickerSelectorTest {
    @Test
    void uniformBoundedDrawGivesEveryEligibleContestantOneSlotAndExcludesExemptPlayers() {
        AtomicInteger nextIndex = new AtomicInteger();
        PickerSelector selector =
                new PickerSelector(
                        bound -> {
                            assertEquals(3, bound);
                            return nextIndex.getAndIncrement();
                        });
        List<PickerSelector.Candidate> candidates =
                List.of(
                        candidate("Ava", false),
                        candidate("Parent", true),
                        candidate("Bo", false),
                        candidate("Cy", false));

        List<String> selectedNames = new ArrayList<>();
        for (int draw = 0; draw < 3; draw++) {
            selectedNames.add(selector.select(candidates).orElseThrow().playerName());
        }

        assertEquals(List.of("Ava", "Bo", "Cy"), selectedNames);
    }

    @Test
    void returnsEmptyWhenEveryCandidateIsExempt() {
        PickerSelector selector = new PickerSelector(ignored -> 0);

        assertTrue(selector.select(List.of(candidate("Parent", true))).isEmpty());
    }

    private static PickerSelector.Candidate candidate(String name, boolean exempt) {
        return new PickerSelector.Candidate(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, exempt);
    }
}
