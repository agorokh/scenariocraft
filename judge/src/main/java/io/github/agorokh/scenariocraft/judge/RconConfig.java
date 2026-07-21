package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Validated Source-RCON settings loaded from environment or optional judge.yml. */
record RconConfig(String host, int port, String password, Duration timeout) {
    static final int MAX_CONFIG_BYTES = 16 * 1024;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private static final Set<String> RCON_KEYS =
            Set.of("host", "port", "password", "timeout-seconds");

    RconConfig {
        if (host == null || host.isBlank() || host.length() > 253
                || host.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RCON host must be a safe non-blank hostname");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("RCON port must be between 1 and 65535");
        }
        if (password == null || password.isBlank() || password.length() > 256
                || password.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RCON password must be a safe non-blank value");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("RCON timeout must be between 1 and 60 seconds");
        }
    }

    static Optional<RconConfig> load(Path configPath, Map<String, String> environment)
            throws IOException {
        Map<String, Object> file = loadFile(configPath);
        String host = configuredString(environment, "SCENARIOCRAFT_RCON_HOST", file.get("host"));
        String password = configuredString(
                environment, "SCENARIOCRAFT_RCON_PASSWORD", file.get("password"));
        Integer port = configuredInteger(
                environment, "SCENARIOCRAFT_RCON_PORT", file.get("port"));
        Integer timeoutSeconds = configuredInteger(
                environment,
                "SCENARIOCRAFT_RCON_TIMEOUT_SECONDS",
                file.getOrDefault("timeout-seconds", DEFAULT_TIMEOUT_SECONDS));
        if (timeoutSeconds == null) {
            throw new IllegalArgumentException(
                    "SCENARIOCRAFT_RCON_TIMEOUT_SECONDS must be configured as an integer");
        }

        boolean anyConfigured = host != null || password != null || port != null;
        if (!anyConfigured) {
            return Optional.empty();
        }
        if (host == null || password == null || port == null) {
            throw new IllegalArgumentException(
                    "RCON host, port, and password must be configured together");
        }
        return Optional.of(new RconConfig(
                host, port, password, Duration.ofSeconds(timeoutSeconds)));
    }

    private static Map<String, Object> loadFile(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("judge.yml must be a regular non-symbolic-link file");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
        }
        if (bytes.length > MAX_CONFIG_BYTES) {
            throw new IOException("judge.yml exceeds the byte limit");
        }
        LoadSettings settings = LoadSettings.builder()
                .setLabel(path.toString())
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(0)
                .build();
        Object loaded = new Load(settings).loadFromString(new String(bytes, StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("judge.yml must contain a mapping");
        }
        requireExactKeys(root, Set.of("rcon"), "judge.yml");
        if (!(root.get("rcon") instanceof Map<?, ?> rcon)) {
            throw new IllegalArgumentException("judge.yml rcon must be a mapping");
        }
        Set<String> actual = stringKeys(rcon);
        if (!RCON_KEYS.containsAll(actual)) {
            throw new IllegalArgumentException("judge.yml rcon contains an unexpected field");
        }
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : rcon.entrySet()) {
            values.put((String) entry.getKey(), entry.getValue());
        }
        return values;
    }

    private static void requireExactKeys(Map<?, ?> value, Set<String> expected, String label) {
        if (!stringKeys(value).equals(expected)) {
            throw new IllegalArgumentException(
                    label + " must contain exactly these keys: " + expected);
        }
    }

    private static Set<String> stringKeys(Map<?, ?> value) {
        Set<String> keys = new java.util.HashSet<>();
        for (Object key : value.keySet()) {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException("configuration keys must be strings");
            }
            keys.add(text);
        }
        return Set.copyOf(keys);
    }

    private static String configuredString(
            Map<String, String> environment, String name, Object fileValue) {
        if (environment.containsKey(name)) {
            return environment.get(name);
        }
        if (fileValue == null) {
            return null;
        }
        if (!(fileValue instanceof String text)) {
            throw new IllegalArgumentException(name + " must be configured as text");
        }
        return text;
    }

    private static Integer configuredInteger(
            Map<String, String> environment, String name, Object fileValue) {
        if (environment.containsKey(name)) {
            String value = environment.get(name);
            if (value == null || !value.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException(name + " must be a positive integer");
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " is outside the integer range", exception);
            }
        }
        if (fileValue == null) {
            return null;
        }
        if (!(fileValue instanceof Integer integer)) {
            throw new IllegalArgumentException(name + " must be configured as an integer");
        }
        return integer;
    }
}
