package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.List;

/** Produces compact Bedrock-safe title and chat text from parsed results. */
final class ResultAnnouncementFormatter {
    static final int MAX_CHAT_CODE_POINTS = 120;
    static final int MAX_TITLE_CODE_POINTS = 64;

    private ResultAnnouncementFormatter() {}

    static Announcement format(BattleResultSummary result) {
        List<String> chat = new ArrayList<>();
        chat.add(limit("Build Battle results — " + clean(result.task()), MAX_CHAT_CODE_POINTS));
        for (BattleResultSummary.ContestantFeedback contestant : result.contestants()) {
            for (BattleResultSummary.PersonaFeedback feedback : contestant.feedback()) {
                chat.add(limit(
                        clean(contestant.player())
                                + " — "
                                + clean(feedback.persona())
                                + ": "
                                + feedback.score()
                                + " — "
                                + clean(feedback.comment()),
                        MAX_CHAT_CODE_POINTS));
            }
        }
        String title;
        if (result.hasWinner()) {
            title = limit("Winner: " + clean(result.winner().player()) + "!", MAX_TITLE_CODE_POINTS);
            chat.add(title);
        } else {
            title = "Build Battle results";
            chat.add(limit(clean(result.noWinnerReason()), MAX_CHAT_CODE_POINTS));
        }
        return new Announcement(title, List.copyOf(chat));
    }

    private static String clean(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int codePoint : value.codePoints().toArray()) {
            boolean whitespace = Character.isWhitespace(codePoint);
            if (whitespace) {
                if (!previousWhitespace) {
                    cleaned.append(' ');
                }
            } else if (codePoint == '{' || codePoint == '}'
                    || codePoint == '[' || codePoint == ']') {
                cleaned.append(codePoint == '{' || codePoint == '[' ? '(' : ')');
            } else {
                cleaned.appendCodePoint(codePoint);
            }
            previousWhitespace = whitespace;
        }
        return cleaned.toString().trim();
    }

    private static String limit(String value, int maximumCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maximumCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maximumCodePoints - 1);
        return value.substring(0, end).stripTrailing() + "…";
    }

    record Announcement(String title, List<String> chatLines) {
        Announcement {
            chatLines = List.copyOf(chatLines);
        }
    }
}
