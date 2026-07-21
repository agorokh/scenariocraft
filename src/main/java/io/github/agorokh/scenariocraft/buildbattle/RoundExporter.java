package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Optional;

/** Receives one immutable round description when BUILDING enters REVEAL. */
@FunctionalInterface
interface RoundExporter extends AutoCloseable {
    void export(RoundExportRequest request);

    /** Identifies the round most recently published successfully by this exporter. */
    default Optional<String> currentRoundId() {
        return Optional.empty();
    }

    /** Invalidates any snapshot that could still be reading mutable arena blocks. */
    default void cancel() {}

    /** Reports whether a snapshot or immutable write is still in flight. */
    default boolean isBusy() {
        return false;
    }

    /** Reports whether mutable arena blocks are still being prepared or read. */
    default boolean isReadingArena() {
        return false;
    }

    @Override
    default void close() {}
}
