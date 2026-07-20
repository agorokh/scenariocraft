package io.github.agorokh.scenariocraft.judge;

final class JudgeException extends Exception {
    JudgeException(String message) {
        super(message);
    }

    JudgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
