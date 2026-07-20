package io.github.agorokh.scenariocraft.buildbattle;

/** Receives one immutable round description when BUILDING enters REVEAL. */
@FunctionalInterface
interface RoundExporter extends AutoCloseable {
    void export(RoundExportRequest request);

    /** Invalidates any snapshot that could still be reading mutable arena blocks. */
    default void cancel() {}

    /** Reports whether a snapshot or immutable write is still in flight. */
    default boolean isBusy() {
        return false;
    }

    @Override
    default void close() {}
}
