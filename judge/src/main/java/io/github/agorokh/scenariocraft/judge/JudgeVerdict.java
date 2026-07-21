package io.github.agorokh.scenariocraft.judge;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record JudgeVerdict(String persona, String reasoning, Scores scores, String comment) {
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?=\\s+|$)");
    private static final Pattern INITIALISM = Pattern.compile("(?:[A-Za-z]\\.){2,}");
    private static final Pattern CRUEL_LANGUAGE = Pattern.compile(
            "\\b(?:awful|bad|boring|dumb|garbage|hate|idiot|lazy|loser|pathetic|"
                    + "stupid|sucks?|terrible|trash|ugly|useless|worst)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRENGTH_LANGUAGE = Pattern.compile(
            "\\b(?:beautiful|bold|bright|charming|clear|clever|colorful|colourful|"
                    + "cozy|creative|delightful|detailed|good|great|impressive|inviting|"
                    + "lovely|neat|nice|solid|strong|sturdy|tidy|warm|welcoming|works)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> ABBREVIATIONS = Set.of(
            "dr.", "e.g.", "etc.", "i.e.", "mr.", "mrs.", "ms.", "prof.", "vs.");

    JudgeVerdict {
        if (persona == null || persona.isBlank()) {
            throw new IllegalArgumentException("persona must be non-blank");
        }
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("reasoning must be non-blank");
        }
        if (scores == null) {
            throw new IllegalArgumentException("scores must be present");
        }
        validateComment(comment);
    }

    double score() {
        return scores.mean();
    }

    private static void validateComment(String text) {
        if (text == null || text.isBlank() || text.chars().anyMatch(JudgeVerdict::isUnsafeControl)) {
            throw new IllegalArgumentException("comment must contain safe single-line text");
        }
        String normalized = text.strip();
        if (!text.equals(normalized)) {
            throw new IllegalArgumentException("comment must not have surrounding whitespace");
        }
        Matcher matcher = SENTENCE_END.matcher(normalized);
        int count = 0;
        int firstSentenceEnd = -1;
        int lastSentenceEnd = -1;
        while (matcher.find()) {
            if (isAbbreviation(normalized, matcher.start(), matcher.end())) {
                continue;
            }
            count++;
            firstSentenceEnd = firstSentenceEnd < 0 ? matcher.end() : firstSentenceEnd;
            lastSentenceEnd = matcher.end();
        }
        if (count != 2 || lastSentenceEnd != normalized.length()) {
            throw new IllegalArgumentException("comment must contain exactly two sentences");
        }
        if (CRUEL_LANGUAGE.matcher(normalized).find()) {
            throw new IllegalArgumentException("comment must use kid-appropriate language");
        }
        if (!STRENGTH_LANGUAGE.matcher(normalized.substring(0, firstSentenceEnd)).find()) {
            throw new IllegalArgumentException("comment must name a genuine strength first");
        }
    }

    private static boolean isUnsafeControl(int codePoint) {
        return codePoint == '\n' || codePoint == '\r'
                || (Character.isISOControl(codePoint) && codePoint != '\t');
    }

    private static boolean isAbbreviation(String text, int punctuationStart, int punctuationEnd) {
        if (punctuationEnd >= text.strip().length()
                || punctuationEnd - punctuationStart != 1
                || text.charAt(punctuationStart) != '.') {
            return false;
        }
        int tokenStart = punctuationStart;
        while (tokenStart > 0 && !Character.isWhitespace(text.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        String token = text.substring(tokenStart, punctuationEnd);
        return ABBREVIATIONS.contains(token.toLowerCase()) || INITIALISM.matcher(token).matches();
    }
}
