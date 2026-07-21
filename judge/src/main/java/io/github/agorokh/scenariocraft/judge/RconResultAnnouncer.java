package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Requests the plugin-owned title/chat/particle announcement through RCON. */
final class RconResultAnnouncer implements RoundResultAnnouncer {
    private static final String ANNOUNCE_COMMAND = "battle announce-results";

    private final Path configPath;
    private final Map<String, String> environment;
    private final RconCommandTransport transport;

    RconResultAnnouncer(Path configPath, Map<String, String> environment) {
        this(configPath, environment, RconClient::execute);
    }

    RconResultAnnouncer(
            Path configPath,
            Map<String, String> environment,
            RconCommandTransport transport) {
        this.configPath = configPath;
        this.environment = Map.copyOf(environment);
        this.transport = transport;
    }

    @Override
    public void announce(JudgeRound round, RoundResults results) throws IOException {
        var config = RconConfig.load(configPath, environment);
        if (config.isPresent()) {
            transport.execute(config.get(), ANNOUNCE_COMMAND);
        }
    }

    @FunctionalInterface
    interface RconCommandTransport {
        void execute(RconConfig config, String command) throws IOException;
    }
}
