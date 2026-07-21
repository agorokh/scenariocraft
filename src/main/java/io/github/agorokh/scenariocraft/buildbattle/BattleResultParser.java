package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict parser for the judge's human-readable, plugin-facing result contract. */
final class BattleResultParser {
    static final int MAX_RESULT_BYTES = 64 * 1024;
    static final int MAX_LINES = 160;
    static final int MAX_SOURCE_LINE_LENGTH = 640;
    static final int MAX_TASK_LENGTH = 512;
    static final int MAX_CONTESTANTS = 8;
    static final int MAX_FEEDBACK_PER_CONTESTANT = 8;
    private static final Pattern CONTESTANT = Pattern.compile("(.{1,80}) \\((p[1-9][0-9]{0,2})\\)");
    private static final Pattern FEEDBACK =
            Pattern.compile("  (.{1,64}): [0-9]+(?:\\.[0-9]{1,2})? — (.{1,500})");
    private static final Pattern WINNER =
            Pattern.compile("Winner: (.{1,80}) with [0-9]+(?:\\.[0-9]{1,2})?");
    private static final Pattern BUILD_FEATURE =
            Pattern.compile(
                    "\\b(?:arch|bridge|build|chimney|color|colour|detail|design|door|doorway|"
                            + "flag|floor|foundation|garden|idea|lighting|outline|palette|path|"
                            + "pattern|proportion|roof|room|shape|silhouette|structure|support|"
                            + "texture|tower|trim|wall|window)s?\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE_EFFECT =
            Pattern.compile(
                    "\\b(?:anchors?|balanced|beautiful|bold|bright|charming|clear|clever|"
                            + "colorful|colourful|cozy|creative|creates?|delightful|detailed|"
                            + "draws?|excellent|fantastic|fits?|frames?|gives?|good|great|"
                            + "impressive|inviting|leads?|lovely|makes?|neat|recognizable|solid|"
                            + "stands? out|strong|sturdy|supports?|tidy|warm|welcoming|works?)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_LANGUAGE =
            Pattern.compile(
                    "\\b(?:awful|bad|boring|disgusting|dumb|embarrassing|failure|garbage|gross|"
                            + "hate|horrible|idiot|incompetent|lazy|loser|nobody|pathetic|pointless|"
                            + "fool|jerk|moron|shame|stupid|sucks?|talentless|terrible|trash|ugly|useless|"
                            + "worthless|worst)\\b|\\b(?:no|without) talent\\b|"
                            + "\\black(?:s|ing)? talent\\b|"
                            + "\\b(?:dead|death|die|harm|hurt|kill|murder|self-harm|suicide)\\b|"
                            + "\\b(?:better off dead|go die|hurt yourself|kill yourself)\\b|"
                            + "\\b(?:arsehole|asshole|bastard|bitch|bullshit|crap|cunt|damn|"
                            + "dick|douche|fuck(?:ed|er|ing)?|motherfucker|piss|prick|shit(?:ty)?)\\b|"
                            + "\\b(?:molest(?:ed|er|ing|ation)?|rape(?:d|s)?|raping|rapist|"
                            + "sexual (?:abuse|assault|violence))\\b|"
                            + "\\b(?:chinks?|coons?|fags?|faggots?|gooks?|kikes?|"
                            + "n[i1]gg(?:a|er)s?|retards?|spics?|trann(?:y|ies)|wetbacks?)\\b",
                    Pattern.CASE_INSENSITIVE);

    BattleResult read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("results.txt is not a regular file");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_RESULT_BYTES + 1);
        }
        if (bytes.length > MAX_RESULT_BYTES) {
            throw new IOException("results.txt exceeds the byte limit");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    BattleResult parse(String contents) {
        if (contents.indexOf('\0') >= 0) {
            throw invalid("contains a NUL character");
        }
        List<String> lines = contents.lines().toList();
        if (lines.size() < 3 || lines.size() > MAX_LINES) {
            throw invalid("has an invalid line count");
        }
        if (lines.stream().anyMatch(line -> line.length() > MAX_SOURCE_LINE_LENGTH)) {
            throw invalid("contains an overlong line");
        }
        String roundId = prefixed(lines.get(0), "Round: ", "round id");
        if (!roundId.matches("round-[0-9]{8}-[0-9]{6}")) {
            throw invalid("has an invalid round id");
        }
        String task =
                displayText(
                        prefixed(lines.get(1), "Task: ", "task"),
                        MAX_TASK_LENGTH,
                        "task");
        List<BattleResult.Contestant> contestants = new ArrayList<>();
        String player = null;
        String plotId = null;
        List<BattleResult.Feedback> feedback = new ArrayList<>();
        String winnerName = null;
        boolean outcomeSeen = false;

        for (int index = 2; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            if (outcomeSeen) {
                throw invalid("contains content after its result record");
            }
            if (line.startsWith("  Mean:") || line.startsWith("  Failed:")) {
                continue;
            }
            Matcher winner = WINNER.matcher(line);
            if (winner.matches()) {
                if (player != null) {
                    addContestant(contestants, player, plotId, feedback);
                    player = null;
                }
                if (contestants.isEmpty()) {
                    throw invalid("places its result before the contestants");
                }
                winnerName = identifierText(winner.group(1), 80, "winner");
                outcomeSeen = true;
                continue;
            }
            if (line.startsWith("No winner:")) {
                if (player != null) {
                    addContestant(contestants, player, plotId, feedback);
                    player = null;
                }
                if (contestants.isEmpty()) {
                    throw invalid("places its result before the contestants");
                }
                outcomeSeen = true;
                continue;
            }
            Matcher contestant = CONTESTANT.matcher(line);
            if (contestant.matches()) {
                if (player != null) {
                    addContestant(contestants, player, plotId, feedback);
                }
                player = identifierText(contestant.group(1), 80, "player");
                plotId = contestant.group(2);
                feedback = new ArrayList<>();
                continue;
            }
            Matcher verdict = FEEDBACK.matcher(line);
            if (verdict.matches() && player != null) {
                if (feedback.size() >= MAX_FEEDBACK_PER_CONTESTANT) {
                    throw invalid("contains too much feedback for one contestant");
                }
                feedback.add(
                        new BattleResult.Feedback(
                                displayText(verdict.group(1), 64, "persona"),
                                feedbackText(verdict.group(2))));
                continue;
            }
            throw invalid("contains an unexpected line");
        }
        if (player != null) {
            addContestant(contestants, player, plotId, feedback);
        }
        if (contestants.isEmpty()) {
            throw invalid("contains no contestants");
        }
        if (!outcomeSeen) {
            throw invalid("is missing its terminal result record");
        }
        Optional<BattleResult.Winner> winner = Optional.empty();
        if (winnerName != null) {
            String selected = winnerName;
            BattleResult.Contestant winningContestant =
                    contestants.stream()
                            .filter(contestant -> contestant.player().equalsIgnoreCase(selected))
                            .findFirst()
                            .orElseThrow(() -> invalid("names a winner who is not a contestant"));
            winner = Optional.of(new BattleResult.Winner(winningContestant.player(), winningContestant.plotId()));
        }
        return new BattleResult(roundId, task, contestants, winner);
    }

    private static void addContestant(
            List<BattleResult.Contestant> contestants,
            String player,
            String plotId,
            List<BattleResult.Feedback> feedback) {
        if (contestants.size() >= MAX_CONTESTANTS) {
            throw invalid("contains too many contestants");
        }
        contestants.add(new BattleResult.Contestant(player, plotId, feedback));
    }

    private static String prefixed(String line, String prefix, String label) {
        if (!line.startsWith(prefix) || line.length() == prefix.length()) {
            throw invalid("is missing " + label);
        }
        return line.substring(prefix.length());
    }

    private static String displayText(String value, int maximumLength, String label) {
        String normalized = structuralText(value, maximumLength, label);
        if (UNSAFE_LANGUAGE.matcher(normalized).find()) {
            throw invalid(label + " must use kid-appropriate language");
        }
        return normalized;
    }

    private static String identifierText(String value, int maximumLength, String label) {
        return structuralText(value, maximumLength, label);
    }

    private static String structuralText(String value, int maximumLength, String label) {
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > maximumLength) {
            throw invalid(label + " has an invalid length");
        }
        if (normalized.indexOf('§') >= 0
                || normalized.codePoints().anyMatch(BattleResultParser::unsafeCodePoint)) {
            throw invalid(label + " contains unsafe characters");
        }
        return normalized;
    }

    private static String feedbackText(String value) {
        String normalized = structuralText(value, 500, "comment");
        Matcher feature = BUILD_FEATURE.matcher(normalized);
        if (!feature.find() || !POSITIVE_EFFECT.matcher(normalized).find()) {
            throw invalid("comment must name a concrete strength");
        }
        return displayText(
                "A positive detail stood out in the "
                        + feature.group().toLowerCase(Locale.ROOT)
                        + ".",
                500,
                "comment");
    }

    private static boolean unsafeCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("results.txt " + detail);
    }
}
