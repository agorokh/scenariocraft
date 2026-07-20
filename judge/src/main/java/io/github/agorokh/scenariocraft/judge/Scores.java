package io.github.agorokh.scenariocraft.judge;

record Scores(int theme_fit, int creativity, int effort, int detail) {
    Scores {
        requireRange("theme_fit", theme_fit);
        requireRange("creativity", creativity);
        requireRange("effort", effort);
        requireRange("detail", detail);
    }

    double mean() {
        return (theme_fit + creativity + effort + detail) / 4.0;
    }

    private static void requireRange(String criterion, int value) {
        if (value < 1 || value > 10) {
            throw new IllegalArgumentException(criterion + " must be between 1 and 10");
        }
    }
}
