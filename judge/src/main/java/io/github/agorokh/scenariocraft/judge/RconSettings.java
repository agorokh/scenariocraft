package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

record RconSettings(
        String host,
        int port,
        String password,
        Duration connectTimeout,
        Duration readTimeout) {
    static final int MAX_CONFIG_BYTES = 64 * 1024;
    private static final Set<String> KEYS =
            Set.of("host", "port", "password", "connect_timeout_seconds", "read_timeout_seconds");
    private static final Set<String> LEGACY_KEYS = Set.of("host", "port", "password", "timeout-seconds");
    private static final Set<String> CONNECTION_ENVIRONMENT_KEYS =
            Set.of(
                    "SCENARIOCRAFT_RCON_HOST",
                    "SCENARIOCRAFT_RCON_PORT",
                    "SCENARIOCRAFT_RCON_PASSWORD");

    RconSettings {
        if (host == null || host.isBlank() || host.length() > 255 || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("RCON host must be a non-blank hostname or address");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("RCON port must be between 1 and 65535");
        }
        if (password == null || password.isBlank() || password.length() > 512
                || password.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RCON password must be a non-blank safe value");
        }
        requireTimeout(connectTimeout, "RCON connect timeout");
        requireTimeout(readTimeout, "RCON read timeout");
    }

    static Optional<RconSettings> load(Path path, Map<String, String> environment) throws IOException {
        Map<?, ?> yaml = Map.of();
        boolean fileConfigured = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        boolean environmentComplete =
                CONNECTION_ENVIRONMENT_KEYS.stream().allMatch(environment::containsKey);
        if (fileConfigured) {
            try {
                yaml = loadYaml(path);
            } catch (IOException | IllegalArgumentException exception) {
                if (!environmentComplete) {
                    throw exception;
                }
            }
        }
        boolean environmentConfigured =
                CONNECTION_ENVIRONMENT_KEYS.stream().anyMatch(environment::containsKey)
                        || environment.containsKey("SCENARIOCRAFT_RCON_CONNECT_TIMEOUT_SECONDS")
                        || environment.containsKey("SCENARIOCRAFT_RCON_READ_TIMEOUT_SECONDS")
                        || environment.containsKey("SCENARIOCRAFT_RCON_TIMEOUT_SECONDS");
        if (!fileConfigured && !environmentConfigured) {
            return Optional.empty();
        }
        String host = stringValue(environment, "SCENARIOCRAFT_RCON_HOST", yaml, "host", "127.0.0.1");
        int port = integerValue(environment, "SCENARIOCRAFT_RCON_PORT", yaml, "port", 25_575);
        String password = stringValue(environment, "SCENARIOCRAFT_RCON_PASSWORD", yaml, "password", null);
        int connectSeconds = timeoutValue(
                environment, "SCENARIOCRAFT_RCON_CONNECT_TIMEOUT_SECONDS", yaml, "connect_timeout_seconds");
        int readSeconds = timeoutValue(
                environment, "SCENARIOCRAFT_RCON_READ_TIMEOUT_SECONDS", yaml, "read_timeout_seconds");
        return Optional.of(
                new RconSettings(
                        host,
                        port,
                        password,
                        Duration.ofSeconds(connectSeconds),
                        Duration.ofSeconds(readSeconds)));
    }

    private static Map<?, ?> loadYaml(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("judge.yml must be a regular file");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
        }
        if (bytes.length > MAX_CONFIG_BYTES) {
            throw new IOException("judge.yml exceeds the byte limit");
        }
        LoadSettings loadSettings =
                LoadSettings.builder()
                        .setLabel(path.toString())
                        .setAllowDuplicateKeys(false)
                        .setMaxAliasesForCollections(10)
                        .build();
        Object loaded;
        try {
            loaded = new Load(loadSettings).loadFromString(new String(bytes, StandardCharsets.UTF_8));
        } catch (YamlEngineException exception) {
            throw new IllegalArgumentException("judge.yml contains invalid YAML", exception);
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("judge.yml must contain a mapping");
        }
        if (!root.keySet().equals(Set.of("rcon"))) {
            throw new IllegalArgumentException("judge.yml must contain exactly the rcon key");
        }
        if (!(root.get("rcon") instanceof Map<?, ?> rcon)) {
            throw new IllegalArgumentException("judge.yml rcon must contain a mapping");
        }
        Set<String> actual =
                rcon.keySet().stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(KEYS) && !actual.equals(LEGACY_KEYS)) {
            throw new IllegalArgumentException(
                    "judge.yml rcon must contain either the current or legacy timeout keys");
        }
        return rcon;
    }

    private static int timeoutValue(
            Map<String, String> environment, String environmentName, Map<?, ?> yaml, String yamlName) {
        if (environment.containsKey(environmentName)) {
            return integerValue(environment, environmentName, Map.of(), yamlName, 5);
        }
        if (environment.containsKey("SCENARIOCRAFT_RCON_TIMEOUT_SECONDS")) {
            return integerValue(environment, "SCENARIOCRAFT_RCON_TIMEOUT_SECONDS", Map.of(), yamlName, 5);
        }
        return integerValue(environment, environmentName, yaml, yaml.containsKey(yamlName) ? yamlName : "timeout-seconds", 5);
    }

    private static String stringValue(
            Map<String, String> environment,
            String environmentName,
            Map<?, ?> yaml,
            String yamlName,
            String fallback) {
        Object value = environment.containsKey(environmentName) ? environment.get(environmentName) : yaml.get(yamlName);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(yamlName + " must be a string");
        }
        return text;
    }

    private static int integerValue(
            Map<String, String> environment,
            String environmentName,
            Map<?, ?> yaml,
            String yamlName,
            int fallback) {
        if (environment.containsKey(environmentName)) {
            String value = environment.get(environmentName);
            if (value == null || !value.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException(environmentName + " must be a positive integer");
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(environmentName + " must be a positive integer", exception);
            }
        }
        Object value = yaml.get(yamlName);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(yamlName + " must be an integer");
        }
        return integer;
    }

    private static void requireTimeout(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException(label + " must be between 1 and 60 seconds");
        }
    }
}
