package io.github.agorokh.scenariocraft.judge;

import java.util.Objects;

record Persona(String name, String voice) {
    Persona {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(voice, "voice");
        if (name.isBlank() || voice.isBlank()) {
            throw new IllegalArgumentException("persona name and voice must be non-blank");
        }
    }
}
