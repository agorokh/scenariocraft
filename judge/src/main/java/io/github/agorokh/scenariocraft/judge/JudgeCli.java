package io.github.agorokh.scenariocraft.judge;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public final class JudgeCli {
    private JudgeCli() {}

    public static void main(String[] arguments) {
        int status = run(
                arguments,
                System.getenv(),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(
            String[] arguments,
            Map<String, String> environment,
            PrintWriter output,
            PrintWriter diagnostics) {
        if (arguments.length < 2 || arguments.length > 3
                || !"--round".equals(arguments[0])
                || (arguments.length == 3 && !"--dry-run".equals(arguments[2]))) {
            diagnostics.println("Usage: judge --round <round-directory> [--dry-run]");
            return 2;
        }
        boolean dryRun = arguments.length == 3;
        PersonaJudge judge;
        if (dryRun) {
            judge = new StubPersonaJudge();
        } else {
            String apiKey = environment.get("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                diagnostics.println(
                        "OPENAI_API_KEY is required for a live run; use --dry-run for offline judging.");
                return 2;
            }
            String timeoutText = environment.getOrDefault(
                    "SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS", "90");
            if (!timeoutText.matches("[1-9][0-9]*")) {
                diagnostics.println(
                        "SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS must be a positive integer.");
                return 2;
            }
            try {
                judge = new OpenAiPersonaJudge(
                        apiKey, Duration.ofSeconds(Integer.parseInt(timeoutText)));
            } catch (NumberFormatException exception) {
                diagnostics.println(
                        "SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS is too large.");
                return 2;
            }
        }
        Path configDirectory;
        try {
            configDirectory = configDirectory(environment);
        } catch (IllegalArgumentException exception) {
            diagnostics.println("SCENARIOCRAFT_JUDGE_CONFIG_DIR must be a valid non-blank path.");
            return 2;
        }
        return new JudgeApplication().run(
                Path.of(arguments[1]),
                configDirectory.resolve("personas.yml"),
                configDirectory.resolve("rubric.md"),
                judge,
                output,
                diagnostics);
    }

    static Path configDirectory(Map<String, String> environment) {
        String value = environment.getOrDefault("SCENARIOCRAFT_JUDGE_CONFIG_DIR", "judge");
        if (value.isBlank()) {
            throw new IllegalArgumentException("config directory must be non-blank");
        }
        return Path.of(value);
    }
}
