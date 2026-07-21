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
        assertTrue(announcement.chatLines().stream().allMatch(line ->
                line.codePointCount(0, line.length())
                        <= ResultAnnouncementFormatter.MAX_CHAT_CODE_POINTS));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("Builder Bob: 8.75 —")));
        assertTrue(announcement.chatLines().stream()
                .anyMatch(line -> line.equals("Winner: Alex!")));
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

        assertEquals("Build Battle results", announcement.title());
        assertTrue(announcement.chatLines().contains(
                "No winner yet — every build showed imagination. The judges will take another look."));
        assertFalse(announcement.chatLines().stream()
                .anyMatch(line -> line.contains("successful verdict") || line.contains("Failed:")));
    }
}
