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
        Path roundDirectory;
        try {
            roundDirectory = Path.of(arguments[1]);
        } catch (IllegalArgumentException exception) {
            diagnostics.println("Round directory must be a valid path.");
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
            try {
                judge = new OpenAiPersonaJudge(
                        apiKey,
                        configuredDuration(
                                environment, "SCENARIOCRAFT_JUDGE_CONNECT_TIMEOUT_SECONDS", 10),
                        configuredDuration(
                                environment, "SCENARIOCRAFT_JUDGE_TIMEOUT_SECONDS", 90));
            } catch (IllegalArgumentException exception) {
                diagnostics.println(exception.getMessage());
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
        Path personasPath = configDirectory.resolve("personas.yml");
        Path rubricPath = configDirectory.resolve("rubric.md");
        if (!java.nio.file.Files.isRegularFile(personasPath)
                || !java.nio.file.Files.isRegularFile(rubricPath)) {
            diagnostics.println("Judge config files were not found under "
                    + configDirectory.toAbsolutePath().normalize()
                    + "; set SCENARIOCRAFT_JUDGE_CONFIG_DIR to their directory.");
            return 2;
        }
        return new JudgeApplication().run(
                roundDirectory,
                personasPath,
                rubricPath,
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

    static Duration configuredDuration(
            Map<String, String> environment, String name, int defaultSeconds) {
        String value = environment.getOrDefault(name, Integer.toString(defaultSeconds));
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive integer.");
        }
        try {
            return Duration.ofSeconds(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " is too large.", exception);
        }
    }
}
