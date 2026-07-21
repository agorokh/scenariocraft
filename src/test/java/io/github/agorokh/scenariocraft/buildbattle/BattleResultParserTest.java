package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BattleResultParserTest {
    private final BattleResultParser parser = new BattleResultParser();
    private final BattleResultFormatter formatter = new BattleResultFormatter();

    @Test
    void formatsPersonaFeedbackIntoBoundedChatWithoutRawJsonSyntax() {
        String longComment = "A bright roof {\"raw\":true} makes this castle feel welcoming. " + "sparkly ".repeat(40);
        BattleResult result =
                parser.parse(
                        """
                        Round: round-20260721-193000
                        Task: A rocket-powered castle

                        Alex (p1)
                          Captain Comet: 9.25 — %s
                          Mean: 9.25

                        Winner: Alex with 9.25
                        """.formatted(longComment));

        List<String> lines = formatter.chatLines(result);

        assertEquals("Alex wins!", formatter.title(result));
        assertTrue(lines.stream().allMatch(line -> line.length() <= BattleResultFormatter.MAX_CHAT_LENGTH));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Captain Comet on Alex:")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("{") || line.contains("}")));
    }

    @Test
    void noWinnerUsesFriendlyTextInsteadOfJudgeFailureDetails() {
        BattleResult result =
                parser.parse(
                        """
                        Round: round-20260721-193000
                        Task: A moon base for cats

                        Alex (p1)
                          Captain Comet: 8.00 — The windows make a lovely star pattern.

                        No winner: internal timeout from provider
                        """);

        assertTrue(result.winner().isEmpty());
        assertTrue(formatter.chatLines(result).getLast().contains("need another look"));
        assertFalse(formatter.chatLines(result).toString().contains("provider"));
    }

    @Test
    void rejectsJsonAndOverlongSourceLinesInsteadOfEchoingThem() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"winner\":\"Alex\"}"));
        String overlong = "x".repeat(BattleResultParser.MAX_SOURCE_LINE_LENGTH + 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("Round: round-20260721-193000\nTask: T\n" + overlong));
    }

    @Test
    void rejectsCruelCopiedFeedbackBeforeItCanReachPlayers() {
        String cruel =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — You are stupid.

                Winner: Alex with 2.00
                """;

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> parser.parse(cruel));

        assertTrue(failure.getMessage().contains("kid-appropriate"));
    }

    @Test
    void rejectsSelfHarmLanguageBeforeItCanReachPlayers() {
        String unsafe =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — You should kill yourself.

                Winner: Alex with 2.00
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(unsafe));
    }
}
