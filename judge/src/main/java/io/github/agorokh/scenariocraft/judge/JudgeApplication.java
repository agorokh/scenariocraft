package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JudgeApplication {
    int run(
            Path roundDirectory,
            Path personasPath,
            Path rubricPath,
            PersonaJudge judge,
            PrintWriter output,
            PrintWriter diagnostics) {
        try {
            if (!Files.isDirectory(roundDirectory)) {
                throw new IOException("Round directory does not exist: " + roundDirectory);
            }
            JudgeRound round = JudgeRound.read(roundDirectory.resolve("manifest.json"));
            JudgeConfig config = JudgeConfig.load(personasPath, rubricPath);
            Map<String, List<Path>> images = new LinkedHashMap<>();
            for (JudgeRound.Plot plot : round.plots()) {
                images.put(plot.plotId(), RoundImages.prepare(roundDirectory, plot.plotId()));
            }
            RoundResults results =
                    JudgeCouncil.judge(round, config, images, judge, diagnostics);
            ResultsWriter.write(roundDirectory, results);
            output.print(ResultsWriter.humanReadable(results));
            output.flush();
            diagnostics.flush();
            return results.hasWinner() ? 0 : 1;
        } catch (IOException | IllegalArgumentException exception) {
            diagnostics.println("Judge failed: " + exception.getMessage());
            diagnostics.flush();
            return 1;
        }
    }
}
