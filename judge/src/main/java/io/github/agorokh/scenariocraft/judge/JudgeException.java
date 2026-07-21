package io.github.agorokh.scenariocraft.judge;

final class JudgeException extends Exception {
    private final boolean retryable;

    JudgeException(String message) {
        this(message, null, true);
    }

    JudgeException(String message, Throwable cause) {
        this(message, cause, true);
    }

    JudgeException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}
