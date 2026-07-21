package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;

/** Complete configuration loaded at plugin enable. */
public record BattleSettings(
        ArenaSettings arena,
        PhaseTimings timings,
        List<String> tasks,
        List<String> exemptNames,
        boolean allowAnyStart,
        int resultsPollTicks) {
    public BattleSettings {
        arena = java.util.Objects.requireNonNull(arena, "arena");
        timings = java.util.Objects.requireNonNull(timings, "timings");
        tasks = List.copyOf(tasks);
        exemptNames = List.copyOf(exemptNames);
        if (resultsPollTicks < 1) {
            throw new IllegalArgumentException("resultsPollTicks must be positive");
        }
    }

    public boolean canStart(String senderName, boolean operator) {
        return allowAnyStart
                || operator
                || exemptNames.stream().anyMatch(name -> name.equalsIgnoreCase(senderName));
    }

    public boolean isExempt(String playerName) {
        return exemptNames.stream().anyMatch(name -> name.equalsIgnoreCase(playerName));
    }
}
