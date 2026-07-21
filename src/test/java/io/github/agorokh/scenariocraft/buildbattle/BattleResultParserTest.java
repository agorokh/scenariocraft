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
        assertTrue(lines.stream().anyMatch(line -> line.contains("positive detail stood out in the roof")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("{") || line.contains("}")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("sparkly")));
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
    void acceptsTheJudgeTaskLengthContractAndRejectsLongerTasks() {
        String template =
                """
                Round: round-20260721-193000
                Task: %s

                Alex (p1)
                  Captain Comet: 9.00 — The bright roof is welcoming.
                Winner: Alex with 9.00
                """;

        BattleResult maximum = parser.parse(template.formatted("x".repeat(512)));

        assertEquals(512, maximum.task().length());
        assertTrue(formatter.chatLines(maximum).getFirst().length() <= BattleResultFormatter.MAX_CHAT_LENGTH);
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(template.formatted("x".repeat(513))));
    }

    @Test
    void cruelCopiedFeedbackIsReducedBeforeItCanReachPlayers() {
        String cruel =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — The bright build is welcoming, and you are stupid.

                Winner: Alex with 2.00
                """;

        BattleResult result = parser.parse(cruel);

        assertEquals(
                "A positive detail stood out in the build.",
                result.contestants().getFirst().feedback().getFirst().comment());
        assertFalse(formatter.chatLines(result).toString().contains("stupid"));
    }

    @Test
    void selfHarmLanguageInCopiedProseIsNotRetainedForPlayers() {
        String unsafe =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — The bright roof is welcoming, and you should kill yourself.

                Winner: Alex with 2.00
                """;

        BattleResult result = parser.parse(unsafe);

        assertFalse(formatter.chatLines(result).toString().contains("kill"));
    }

    @Test
    void unsafeCopiedProseIsReducedWhileMinecraftFormattingCodesAreRejected() {
        String template =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — %s

                Winner: Alex with 2.00
                """;

        for (String copiedProse :
                List.of(
                        "The bright roof is welcoming, damn it.",
                        "The bright roof is welcoming, you fool.",
                        "The bright roof is welcoming, followed by unsafe arbitrary prose.")) {
            BattleResult result = parser.parse(template.formatted(copiedProse));
            assertEquals(
                    "A positive detail stood out in the roof.",
                    result.contestants().getFirst().feedback().getFirst().comment());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(template.formatted("The §kobfuscated roof is clever.")));
    }

    @Test
    void copiedFeedbackMustNameAConcreteStrength() {
        String template =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 2.00 — %s

                Winner: Alex with 2.00
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(template.formatted("I don't know.")));
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(template.formatted("Try adding more.")));
        BattleResult safeSummary =
                parser.parse(
                        template.formatted(
                                "The bright roof is clever, followed by arbitrary moron prose."));
        assertEquals(
                "A positive detail stood out in the roof.",
                safeSummary.contestants().getFirst().feedback().getFirst().comment());
    }

    @Test
    void legalPlayerIdentifiersAreNotRejectedAsProse() {
        BattleResult result =
                parser.parse(
                        """
                        Round: round-20260721-193000
                        Task: A moon base for cats

                        Trash (p1)
                          Captain Comet: 9.00 — The bright roof is welcoming.
                        Winner: Trash with 9.00
                        """);

        assertEquals("Trash", result.contestants().getFirst().player());
        assertEquals("Trash", result.winner().orElseThrow().player());

        BattleResult bedrock =
                parser.parse(
                        """
                        Round: round-20260721-193000
                        Task: A moon base for cats

                        .Bedrock_Kid (p1)
                          Captain Comet: 9.00 — The bright roof is welcoming.
                        Winner: .Bedrock_Kid with 9.00
                        """);
        assertEquals(".Bedrock_Kid", bedrock.contestants().getFirst().player());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        parser.parse(
                                """
                                Round: round-20260721-193000
                                Task: A moon base for cats

                                You should hurt yourself (p1)
                                  Captain Comet: 9.00 — The bright roof is welcoming.
                                Winner: You should hurt yourself with 9.00
                                """));
    }

    @Test
    void resultRecordMustBeUniqueAndTerminalAfterContestants() {
        String contradictory =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Winner: Alex with 9.00
                Alex (p1)
                  Captain Comet: 9.00 — The bright roof is welcoming.
                No winner: tied panel
                """;
        String contentAfterWinner =
                """
                Round: round-20260721-193000
                Task: A moon base for cats

                Alex (p1)
                  Captain Comet: 9.00 — The bright roof is welcoming.
                Winner: Alex with 9.00
                No winner: tied panel
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(contradictory));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(contentAfterWinner));
    }

    @Test
    void rejectsMoreFeedbackThanTheJudgePersonaLimit() {
        String feedback =
                "  Captain Comet: 2.00 — The bright roof is welcoming.\n"
                        .repeat(BattleResultParser.MAX_FEEDBACK_PER_CONTESTANT + 1);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        parser.parse(
                                """
                                Round: round-20260721-193000
                                Task: A moon base for cats

                                Alex (p1)
                                %s
                                Winner: Alex with 2.00
                                """
                                        .formatted(feedback)));
    }

    @Test
    void missingPersonaVerdictsProduceAnHonestRetryMessage() {
        BattleResult result =
                parser.parse(
                        """
                        Round: round-20260721-193000
                        Task: A moon base for cats

                        Alex (p1)
                          Failed: provider timeout

                        No winner: not enough verdicts
                        """);

        List<String> lines = formatter.chatLines(result);
        String feedback = lines.get(1);
        assertTrue(feedback.contains("couldn't finish feedback"));
        assertFalse(feedback.contains("cheering"));
        assertEquals("No winner this time — the judges need another look.", lines.getLast());
        assertEquals("Judges need another look", formatter.title(result));
    }
}
