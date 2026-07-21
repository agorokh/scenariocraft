package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultAnnouncementFormatterTest {
    @Test
    void announcementLinesAreBoundedAndNeverExposeJsonSyntax() {
        BattleResultSummary result =
                BattleResultsReader.parse(BattleResultsReaderTest.winningResults());

        ResultAnnouncementFormatter.Announcement announcement =
                ResultAnnouncementFormatter.format(result);

        assertTrue(announcement.title().codePointCount(0, announcement.title().length())
                <= ResultAnnouncementFormatter.MAX_TITLE_CODE_POINTS);
        assertTrue(announcement.chatLines().stream().allMatch(line -> {
            String visibleLine = line.replaceAll("§.", "");
            return visibleLine.codePointCount(0, visibleLine.length())
                    <= ResultAnnouncementFormatter.MAX_CHAT_CODE_POINTS;
        }));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("Builder Bob: 8.75 —")));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.equals("§6Winner: Alex!§r")));
        assertFalse(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("{") || line.contains("}")
                        || line.contains("\"winner\"")));
    }

    @Test
    void quorumFailureUsesWarmKidFacingTextInsteadOfOperationalDiagnostics() {
        BattleResultSummary result = BattleResultsReader.parse("""
                Round: round-20260721-193000
                Task: A dragon treehouse

                Alex (p1)
                  Failed: Builder Bob attempt 2 failed

                No winner: Alex received 0 successful verdicts; at least 2 are required.
                """);

        ResultAnnouncementFormatter.Announcement announcement =
                ResultAnnouncementFormatter.format(result);

        assertEquals("Speed Build results", announcement.title());
        assertTrue(announcement.chatLines().contains(
                "No winner yet — every build showed imagination. The judges will take another look."));
        assertFalse(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("successful verdict") || line.contains("Failed:")));
    }

    @Test
    void stripsLegacyMinecraftFormattingCodesFromJudgeText() {
        BattleResultSummary result = BattleResultsReader.parse(
                BattleResultsReaderTest.winningResults().replace(
                        "The leafy wings are a genuine detail worth celebrating.",
                        "§kHidden castle details are worth celebrating."));

        ResultAnnouncementFormatter.Announcement announcement =
                ResultAnnouncementFormatter.format(result);

        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("Hidden castle details are worth celebrating.")));
        assertFalse(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("§k") || line.contains("kHidden")));
    }

    @Test
    void givesEachJudgePersonaADistinctReadableSignatureColor() {
        BattleResultSummary result = BattleResultsReader.parse("""
                Round: round-20260721-193000
                Task: A dragon treehouse

                Alex (p1)
                  Professor Brickworth: 8.75 — The sturdy shape gives this build character.
                  Captain Sparkle: 9.00 — The bright roof is a delightful focal point.
                  Granny Redstone: 8.50 — The welcoming doorway is a genuine strength.
                  Mean: 8.75

                Winner: Alex with 8.75
                """);

        ResultAnnouncementFormatter.Announcement announcement =
                ResultAnnouncementFormatter.format(result);

        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("§9Professor Brickworth: 8.75§r —")));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("§dCaptain Sparkle: 9.00§r —")));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("§5Granny Redstone: 8.50§r —")));
        assertTrue(announcement.chatLines().stream()
                .filter(line -> line.contains("genuine strength"))
                .allMatch(line -> line.endsWith("§r — The welcoming doorway is a genuine strength.")));
    }

    @Test
    void colorCodesDoNotShortenLongJudgeComments() {
        String longComment = "bright ".repeat(30).trim();
        BattleResultSummary result = BattleResultsReader.parse("""
                Round: round-20260721-193000
                Task: A dragon treehouse

                Alex (p1)
                  Professor Brickworth: 8.75 — %s
                  Mean: 8.75

                Winner: Alex with 8.75
                """.formatted(longComment));

        String verdictLine = ResultAnnouncementFormatter.format(result).chatLines().stream()
                .filter(line -> line.contains("Professor Brickworth"))
                .findFirst()
                .orElseThrow();
        String visibleLine = verdictLine.replaceAll("§.", "");

        assertEquals(ResultAnnouncementFormatter.MAX_CHAT_CODE_POINTS,
                visibleLine.codePointCount(0, visibleLine.length()));
        assertTrue(verdictLine.contains("§9Professor Brickworth: 8.75§r —"));
    }
}
