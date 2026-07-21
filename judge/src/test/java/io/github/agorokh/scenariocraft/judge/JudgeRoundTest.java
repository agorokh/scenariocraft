package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeRoundTest {
    @Test
    void rejectsTerminalControlCharactersInHumanReadableManifestFields() {
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage\u001b[2J", "Alex"));
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage", "Alex\u202e"));
        assertThrows(IllegalArgumentException.class, () -> round("Build a cottage\nHide this", "Alex"));
    }

    private JudgeRound round(String task, String player) {
        return new JudgeRound(
                1,
                "round-20260721-193000",
                task,
                "battle_world",
                List.of(new JudgeRound.Plot("p1", player)));
    }
}
