package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JudgeApplication {
    static final long MAX_ROUND_IMAGE_BYTES = 32L * 1024 * 1024;

    int run(
            Path roundDirectory,
            Path personasPath,
            Path rubricPath,
            PersonaJudge judge,
            PrintWriter output,
            PrintWriter diagnostics) {
        return run(
                roundDirectory,
                personasPath,
                rubricPath,
                judge,
                output,
                diagnostics,
                (round, results) -> {});
    }

    int run(
            Path roundDirectory,
            Path personasPath,
            Path rubricPath,
            PersonaJudge judge,
            PrintWriter output,
            PrintWriter diagnostics,
            RoundResultAnnouncer announcer) {
        try {
            if (!Files.isDirectory(roundDirectory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(roundDirectory)) {
                throw new IOException("Round directory does not exist: " + roundDirectory);
            }
            Path canonicalRound = roundDirectory.toRealPath();
            Files.deleteIfExists(canonicalRound.resolve("results.txt"));
            Files.deleteIfExists(canonicalRound.resolve("results.json"));
            JudgeRound round = JudgeRound.read(canonicalRound.resolve("manifest.json"));
            JudgeConfig config = JudgeConfig.load(personasPath, rubricPath);
            Map<String, List<JudgeImage>> images = new LinkedHashMap<>();
            long totalImageBytes = 0;
            for (JudgeRound.Plot plot : round.plots()) {
                List<JudgeImage> plotImages = RoundImages.prepare(canonicalRound, plot);
                totalImageBytes = Math.addExact(
                        totalImageBytes,
                        plotImages.stream().mapToLong(JudgeImage::size).sum());
                if (totalImageBytes > MAX_ROUND_IMAGE_BYTES) {
                    throw new IOException("Round images exceed the aggregate byte limit");
                }
                images.put(plot.plotId(), plotImages);
            }
            RoundResults results =
                    JudgeCouncil.judge(round, config, images, judge, diagnostics);
            ResultsWriter.write(canonicalRound, results);
            output.print(ResultsWriter.humanReadable(results));
            output.flush();
            try {
                announcer.announce(round, results);
            } catch (IOException announcementFailure) {
                diagnostics.println(
                        "RCON announcement failed ("
                                + safeTransportReason(announcementFailure)
                                + "); results remain available on disk.");
            } catch (IllegalArgumentException announcementFailure) {
                diagnostics.println(
                        "RCON announcement configuration is invalid; results remain available on disk.");
            }
            diagnostics.flush();
            return results.hasWinner() ? 0 : 1;
        } catch (IOException | IllegalArgumentException | JudgeException exception) {
            diagnostics.println("Judge failed: " + exception.getMessage());
            diagnostics.flush();
            return 1;
        }
    }

    private static String safeTransportReason(IOException failure) {
        if (failure instanceof java.net.SocketTimeoutException) {
            return "connection timed out";
        }
        if (failure instanceof java.net.ConnectException) {
            return "connection failed";
        }
        if (failure instanceof java.io.EOFException) {
            return "connection closed early";
        }
        String message = failure.getMessage();
        if (message != null && message.startsWith("RCON ")
                && message.length() <= 200
                && message.codePoints().noneMatch(Character::isISOControl)) {
            return message;
        }
        return "transport error";
    }
}
