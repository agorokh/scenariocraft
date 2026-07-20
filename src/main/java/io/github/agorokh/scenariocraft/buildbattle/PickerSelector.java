package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Selects one uniformly indexed, non-exempt contestant for the secret note. */
final class PickerSelector {
    private final IntUnaryOperator randomIndex;

    PickerSelector(IntUnaryOperator randomIndex) {
        this.randomIndex = Objects.requireNonNull(randomIndex, "randomIndex");
    }

    Optional<Candidate> select(List<Candidate> candidates) {
        List<Candidate> eligible =
                candidates.stream().filter(candidate -> !candidate.exempt()).toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        int selectedIndex = randomIndex.applyAsInt(eligible.size());
        if (selectedIndex < 0 || selectedIndex >= eligible.size()) {
            throw new IllegalStateException("random picker index was outside the eligible list");
        }
        return Optional.of(eligible.get(selectedIndex));
    }

    record Candidate(UUID playerId, String playerName, boolean exempt) {
        Candidate {
            playerId = Objects.requireNonNull(playerId, "playerId");
            playerName = Objects.requireNonNull(playerName, "playerName");
        }
    }
}
