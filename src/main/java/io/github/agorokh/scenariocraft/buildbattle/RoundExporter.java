package io.github.agorokh.scenariocraft.buildbattle;

/** Receives one immutable round description when BUILDING enters REVEAL. */
@FunctionalInterface
interface RoundExporter extends AutoCloseable {
    void export(RoundExportRequest request);

    @Override
    default void close() {}
}
