package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Bounded discovery of judge results below the plugin's rounds directory. */
final class BattleResultRepository {
    private static final int MAX_ROUND_DIRECTORIES = 256;
    private final Path roundsDirectory;
    private final BattleResultParser parser;

    BattleResultRepository(Path roundsDirectory) {
        this(roundsDirectory, new BattleResultParser());
    }

    BattleResultRepository(Path roundsDirectory, BattleResultParser parser) {
        this.roundsDirectory = java.util.Objects.requireNonNull(roundsDirectory, "roundsDirectory");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
    }

    Optional<BattleResult> latest() throws IOException {
        if (!Files.isDirectory(roundsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(roundsDirectory)) {
            return Optional.empty();
        }
        List<Path> rounds;
        try (var paths = Files.list(roundsDirectory)) {
            rounds = paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString().matches("round-[0-9]{8}-[0-9]{6}"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .limit(MAX_ROUND_DIRECTORIES + 1L)
                    .toList();
        }
        if (rounds.size() > MAX_ROUND_DIRECTORIES) {
            throw new IOException("round directory count exceeds " + MAX_ROUND_DIRECTORIES);
        }
        for (Path round : rounds) {
            Path result = round.resolve("results.txt");
            if (!Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                return Optional.of(parser.read(result));
            } catch (IllegalArgumentException malformed) {
                throw new IOException("latest results.txt is malformed", malformed);
            }
        }
        return Optional.empty();
    }

    Optional<BattleResult> round(String roundId) throws IOException {
        if (!roundId.matches("round-[0-9]{8}-[0-9]{6}")) {
            throw new IllegalArgumentException("round id must match round-<yyyymmdd>-<hhmmss>");
        }
        Path result = roundsDirectory.resolve(roundId).resolve("results.txt");
        if (!Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.read(result));
        } catch (IllegalArgumentException malformed) {
            throw new IOException("results.txt is malformed", malformed);
        }
    }
}
