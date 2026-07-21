package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.List;

/** Produces Bedrock-safe, line-bounded titles and chat from parsed result fields. */
final class BattleResultFormatter {
    static final int MAX_CHAT_LENGTH = 180;
    static final int MAX_TITLE_LENGTH = 64;

    List<String> chatLines(BattleResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(clamp("Build Battle results: " + safe(result.task()), MAX_CHAT_LENGTH));
        for (BattleResult.Contestant contestant : result.contestants()) {
            if (contestant.feedback().isEmpty()) {
                lines.add(clamp("The judges are still cheering for " + safe(contestant.player()) + ".", MAX_CHAT_LENGTH));
                continue;
            }
            for (BattleResult.Feedback feedback : contestant.feedback()) {
                lines.add(
                        clamp(
                                safe(feedback.persona())
                                        + " on "
                                        + safe(contestant.player())
                                        + ": "
                                        + safe(feedback.comment()),
                                MAX_CHAT_LENGTH));
            }
        }
        lines.add(
                result.winner()
                        .map(winner -> clamp("Winner: " + safe(winner.player()) + "!", MAX_CHAT_LENGTH))
                        .orElse("Every build had something worth celebrating — the judges need another look!"));
        return List.copyOf(lines);
    }

    String title(BattleResult result) {
        return result.winner()
                .map(winner -> clamp(safe(winner.player()) + " wins!", MAX_TITLE_LENGTH))
                .orElse("Amazing builds!");
    }

    private static String safe(String value) {
        return value.replace('{', '(')
                .replace('}', ')')
                .replace('[', '(')
                .replace(']', ')')
                .replace('`', '\'')
                .replace('"', '\'')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String clamp(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 1).stripTrailing() + "…";
    }
}
