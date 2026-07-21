package io.github.agorokh.scenariocraft.judge;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record JudgeVerdict(String persona, String reasoning, Scores scores, String comment) {
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?=\\s+|$)");
    private static final Pattern INITIALISM = Pattern.compile("(?:[A-Za-z]\\.){2,}");
    private static final Pattern CRUEL_LANGUAGE = Pattern.compile(
            "\\b(?:awful|bad|boring|disgusting|dumb|embarrassing|failure|garbage|gross|"
                    + "hate|horrible|idiot|incompetent|lazy|loser|nobody|pathetic|pointless|"
                    + "shame|stupid|sucks?|talentless|terrible|trash|ugly|useless|"
                    + "worthless|worst)\\b|\\b(?:can't|cannot|don't|doesn't|isn't|lack|"
                    + "lacks|never|no|not|nothing|without|won't)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPROVEMENT_START = Pattern.compile(
            "^(?:add|build|consider|experiment|focus|for the next round|give|keep|make|"
                    + "next|place|try|tuck|use|you can|you could)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRUCTIVE_START = Pattern.compile(
            "^(?:add|build|consider|experiment|focus|for the next round|give|keep|make|"
                    + "next|one next step|place|to make|try|tuck|use|you can|you could|"
                    + "your next step|.{1,60}\\btip is to)\\b",
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
        String firstSentence = normalized.substring(0, firstSentenceEnd).strip();
        String secondSentence = normalized.substring(firstSentenceEnd).strip();
        if (IMPROVEMENT_START.matcher(firstSentence).find()) {
            throw new IllegalArgumentException("comment must name a genuine strength first");
        }
        if (!CONSTRUCTIVE_START.matcher(secondSentence).find()) {
            throw new IllegalArgumentException("comment must end with constructive guidance");
        }
    }

    private static boolean isUnsafeControl(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
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
