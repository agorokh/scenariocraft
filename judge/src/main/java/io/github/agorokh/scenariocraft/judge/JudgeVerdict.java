package io.github.agorokh.scenariocraft.judge;

import java.text.BreakIterator;
import java.util.Locale;

record JudgeVerdict(String persona, String reasoning, Scores scores, String comment) {
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
        if (comment == null || sentenceCount(comment) != 2) {
            throw new IllegalArgumentException("comment must contain exactly two sentences");
        }
    }

    double score() {
        return scores.mean();
    }

    private static int sentenceCount(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);
        int count = 0;
        for (int start = iterator.first(), end = iterator.next();
                end != BreakIterator.DONE;
                start = end, end = iterator.next()) {
            if (!text.substring(start, end).isBlank()) {
                count++;
            }
        }
        return count;
    }
}
