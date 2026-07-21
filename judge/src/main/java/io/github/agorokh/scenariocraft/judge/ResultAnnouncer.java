package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;

@FunctionalInterface
interface ResultAnnouncer {
    void announce(String roundId) throws IOException;
}
