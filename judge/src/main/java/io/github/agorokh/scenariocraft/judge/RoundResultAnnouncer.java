package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;

/** Optional post-publication transport for asking the server to announce a verdict. */
@FunctionalInterface
interface RoundResultAnnouncer {
    void announce(JudgeRound round, RoundResults results) throws IOException;
}
