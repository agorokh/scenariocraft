package io.github.agorokh.scenariocraft.buildbattle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds and strictly parses the newest durable human-readable judge result. */
final class BattleResultsReader {
    static final int MAX_RESULTS_BYTES = 64 * 1024;
    static final int MAX_ROUND_DIRECTORIES = 1_000;
    private static final Pattern ROUND_DIRECTORY =
            Pattern.compile("round-[0-9]{8}-[0-9]{6}");
    private static final Pattern ROUND_LINE =
            Pattern.compile("Round: (round-[0-9]{8}-[0-9]{6})");
    private static final Pattern CONTESTANT_LINE =
            Pattern.compile("(.{1,64}) \\((p[1-9][0-9]*)\\)");
    private static final Pattern FEEDBACK_LINE =
            Pattern.compile("  (.{1,64}): ((?:10|[0-9])\\.[0-9]{2}) — (.{1,500})");
    private static final Pattern MEAN_LINE =
            Pattern.compile("  Mean: (?:10|[0-9])\\.[0-9]{2}");
    private static final Pattern FAILURE_LINE =
            Pattern.compile("  Failed: .{1,512}");
    private static final Pattern WINNER_LINE =
            Pattern.compile("Winner: (.{1,64}) with (?:10|[0-9])\\.[0-9]{2}");

    private final Path roundsDirectory;

    BattleResultsReader(Path roundsDirectory) {
        this.roundsDirectory = roundsDirectory;
    }

    Optional<LatestResult> latest() throws IOException {
        return findLatest(true);
    }

    Optional<LatestResult> latestRound() throws IOException {
        return findLatest(false);
    }

    private Optional<LatestResult> findLatest(boolean skipRoundsWithoutResults)
            throws IOException {
        if (!Files.isDirectory(roundsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(roundsDirectory)) {
            return Optional.empty();
        }
        List<Path> roundDirectories;
        try (var paths = Files.list(roundsDirectory)) {
            roundDirectories = paths
                    .filter(path -> ROUND_DIRECTORY.matcher(path.getFileName().toString()).matches())
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(
                                    (Path path) -> path.getFileName().toString())
                            .reversed())
                    .limit(MAX_ROUND_DIRECTORIES + 1L)
                    .toList();
        }
        if (roundDirectories.size() > MAX_ROUND_DIRECTORIES) {
            throw new IOException("round directory count exceeds the supported limit");
        }
        for (Path roundDirectory : roundDirectories) {
            Path results = roundDirectory.resolve("results.txt");
            if (!Files.isRegularFile(results, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(results)) {
                if (!skipRoundsWithoutResults) {
                    return Optional.empty();
                }
                continue;
            }
            byte[] bytes = readBounded(results);
            BattleResultSummary summary = parse(decodeUtf8(bytes));
            if (!summary.roundId().equals(roundDirectory.getFileName().toString())) {
                throw new IOException("results.txt round does not match its directory");
            }
            return Optional.of(new LatestResult(summary, fingerprint(bytes)));
        }
        return Optional.empty();
    }

    static BattleResultSummary parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("results.txt must not be blank");
        }
        String normalized = text.replace("\r\n", "\n");
        if (normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("results.txt contains unsupported line endings");
        }
        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        if (lines.size() < 4 || lines.size() > 128) {
            throw new IllegalArgumentException("results.txt has an invalid line count");
        }
        Matcher round = ROUND_LINE.matcher(lines.get(0));
        if (!round.matches()) {
            throw new IllegalArgumentException("results.txt has an invalid round header");
        }
        String task = requirePrefixedText(lines.get(1), "Task: ", 512, "task");
        if (!lines.get(2).isEmpty()) {
            throw new IllegalArgumentException("results.txt is missing its header separator");
        }

        List<BattleResultSummary.ContestantFeedback> contestants = new ArrayList<>();
        Set<String> plotIds = new HashSet<>();
        Set<String> players = new HashSet<>();
        int index = 3;
        while (index < lines.size()
                && !lines.get(index).startsWith("Winner: ")
                && !lines.get(index).startsWith("No winner:")) {
            if (lines.get(index).isEmpty()) {
                index++;
                continue;
            }
            Matcher contestant = CONTESTANT_LINE.matcher(lines.get(index));
            if (!contestant.matches()) {
                throw new IllegalArgumentException("results.txt has an invalid contestant line");
            }
            String player = requireSafeText(contestant.group(1), 64, "player");
            String plotId = contestant.group(2);
            if (!players.add(player) || !plotIds.add(plotId)) {
                throw new IllegalArgumentException(
                        "results.txt contestant names and plot ids must be unique");
            }
            index++;
            List<BattleResultSummary.PersonaFeedback> feedback = new ArrayList<>();
            while (index < lines.size() && !lines.get(index).isEmpty()) {
                Matcher persona = FEEDBACK_LINE.matcher(lines.get(index));
                if (persona.matches()) {
                    feedback.add(new BattleResultSummary.PersonaFeedback(
                            requireSafeText(persona.group(1), 64, "persona"),
                            persona.group(2),
                            requireSafeText(persona.group(3), 500, "comment")));
                    if (feedback.size() > 8) {
                        throw new IllegalArgumentException(
                                "results.txt persona feedback exceeds the supported limit");
                    }
                    index++;
                    continue;
                }
                if (MEAN_LINE.matcher(lines.get(index)).matches()
                        || FAILURE_LINE.matcher(lines.get(index)).matches()) {
                    requireSafeText(lines.get(index), 520, "result detail");
                    index++;
                    continue;
                }
                break;
            }
            contestants.add(new BattleResultSummary.ContestantFeedback(
                    plotId, player, feedback));
        }
        if (contestants.isEmpty() || contestants.size() > 8 || index >= lines.size()) {
            throw new IllegalArgumentException("results.txt has no bounded contestant results");
        }

        BattleResultSummary.Winner winner = null;
        String noWinnerReason = null;
        Matcher winnerLine = WINNER_LINE.matcher(lines.get(index));
        if (winnerLine.matches()) {
            String winnerName = requireSafeText(winnerLine.group(1), 64, "winner");
            BattleResultSummary.ContestantFeedback winningContestant = contestants.stream()
                    .filter(contestant -> contestant.player().equals(winnerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "results.txt winner is not a contestant"));
            winner = new BattleResultSummary.Winner(
                    winningContestant.plotId(), winningContestant.player());
        } else if (lines.get(index).startsWith("No winner:")) {
            noWinnerReason = requireSafeText(lines.get(index), 512, "no-winner reason");
        } else {
            throw new IllegalArgumentException("results.txt has no final verdict");
        }
        index++;
        while (index < lines.size() && lines.get(index).isEmpty()) {
            index++;
        }
        if (index != lines.size()) {
            throw new IllegalArgumentException("results.txt contains trailing content");
        }
        return new BattleResultSummary(
                round.group(1), task, contestants, winner, noWinnerReason);
    }

    private static String requirePrefixedText(
            String line, String prefix, int maxLength, String label) {
        if (!line.startsWith(prefix)) {
            throw new IllegalArgumentException("results.txt has an invalid " + label + " line");
        }
        return requireSafeText(line.substring(prefix.length()), maxLength, label);
    }

    private static String requireSafeText(String value, int maxLength, String label) {
        if (value.isBlank() || value.length() > maxLength
                || value.codePoints().anyMatch(BattleResultsReader::isUnsafeCodePoint)) {
            throw new IllegalArgumentException(label + " is not safe player-facing text");
        }
        return value;
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static byte[] readBounded(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_RESULTS_BYTES + 1);
        }
        if (bytes.length > MAX_RESULTS_BYTES) {
            throw new IOException("results.txt exceeds the byte limit");
        }
        return bytes;
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("results.txt is not valid UTF-8", exception);
        }
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record LatestResult(BattleResultSummary summary, String fingerprint) {}
}
