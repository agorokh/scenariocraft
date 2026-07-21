package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.util.Optional;

/** Read seam for bounded, off-thread judge result lookup. */
interface BattleResultReader {
    Optional<BattleResult> latest() throws IOException;

    Optional<BattleResult> round(String roundId) throws IOException;
}
