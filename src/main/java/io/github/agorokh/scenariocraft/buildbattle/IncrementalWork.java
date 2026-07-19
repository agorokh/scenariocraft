package io.github.agorokh.scenariocraft.buildbattle;

/** A lazily consumed unit of bounded main-thread work. */
public interface IncrementalWork {
    boolean hasNext();

    void runNext();

    long remaining();
}
