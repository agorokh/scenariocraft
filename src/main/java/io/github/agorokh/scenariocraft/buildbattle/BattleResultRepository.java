package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/** Bounded discovery of judge results below the plugin's rounds directory. */
final class BattleResultRepository implements BattleResultReader {
    private static final int MAX_RESULT_CANDIDATES = 256;
    private final Path roundsDirectory;
    private final BattleResultParser parser;

    BattleResultRepository(Path roundsDirectory) {
        this(roundsDirectory, new BattleResultParser());
    }

    BattleResultRepository(Path roundsDirectory, BattleResultParser parser) {
        this.roundsDirectory = java.util.Objects.requireNonNull(roundsDirectory, "roundsDirectory");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
    }

    @Override
    public Optional<BattleResult> latest() throws IOException {
        if (!Files.isDirectory(roundsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(roundsDirectory)) {
            return Optional.empty();
        }
        PriorityQueue<Path> recent =
                new PriorityQueue<>(Comparator.comparing(path -> path.getFileName().toString()));
        try (var paths = Files.list(roundsDirectory)) {
            paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString().matches("round-[0-9]{8}-[0-9]{6}"))
                    .filter(
                            path ->
                                    Files.isRegularFile(
                                            path.resolve("results.txt"), LinkOption.NOFOLLOW_LINKS))
                    .forEach(
                            path -> {
                                recent.add(path);
                                if (recent.size() > MAX_RESULT_CANDIDATES) {
                                    recent.remove();
                                }
                            });
        }
        List<Path> rounds =
                recent.stream()
                        .sorted(
                                Comparator.comparing(
                                                (Path path) -> path.getFileName().toString())
                                        .reversed())
                        .toList();
        for (Path round : rounds) {
            Path result = round.resolve("results.txt");
            try {
                BattleResult parsed = parser.read(result);
                if (!parsed.roundId().equals(round.getFileName().toString())) {
                    throw new IOException("latest results.txt round id does not match its directory");
                }
                return Optional.of(parsed);
            } catch (IOException | IllegalArgumentException unreadable) {
                continue;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<BattleResult> round(String roundId) throws IOException {
        if (!roundId.matches("round-[0-9]{8}-[0-9]{6}")) {
            throw new IllegalArgumentException("round id must match round-<yyyymmdd>-<hhmmss>");
        }
        if (!Files.isDirectory(roundsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(roundsDirectory)) {
            return Optional.empty();
        }
        Path round = roundsDirectory.resolve(roundId);
        if (!Files.isDirectory(round, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(round)) {
            return Optional.empty();
        }
        Path result = round.resolve("results.txt");
        if (!Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            BattleResult parsed = parser.read(result);
            if (!parsed.roundId().equals(roundId)) {
                throw new IOException("results.txt round id does not match its directory");
            }
            return Optional.of(parsed);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("results.txt is malformed", malformed);
        }
    }
}
