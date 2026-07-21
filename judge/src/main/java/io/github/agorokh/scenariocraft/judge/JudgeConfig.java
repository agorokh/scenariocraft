package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

record JudgeConfig(int minJudges, List<Persona> personas, String rubric) {
    static final int MAX_CONFIG_BYTES = 64 * 1024;
    static final int MAX_PERSONAS = 8;
    private static final int MAX_RUBRIC_LENGTH = 16 * 1024;
    private static final int MAX_PERSONA_NAME_LENGTH = 64;
    private static final int MAX_PERSONA_VOICE_LENGTH = 512;
    static final List<String> CRITERIA =
            List.of("theme_fit", "creativity", "effort", "detail");

    JudgeConfig {
        personas = List.copyOf(personas);
        if (personas.size() > MAX_PERSONAS) {
            throw new IllegalArgumentException("persona count exceeds " + MAX_PERSONAS);
        }
        if (minJudges < 2) {
            throw new IllegalArgumentException("min_judges must be at least 2");
        }
        if (minJudges > personas.size()) {
            throw new IllegalArgumentException("min_judges cannot exceed persona count");
        }
        if (personas.stream().map(Persona::name).distinct().count() != personas.size()) {
            throw new IllegalArgumentException("persona names must be unique");
        }
        if (rubric == null || rubric.isBlank() || rubric.length() > MAX_RUBRIC_LENGTH) {
            throw new IllegalArgumentException("rubric.md must be non-blank");
        }
        for (String criterion : CRITERIA) {
            if (!rubric.contains(criterion)) {
                throw new IllegalArgumentException(
                        "rubric.md is missing criterion: " + criterion);
            }
        }
    }

    static JudgeConfig load(Path personasPath, Path rubricPath) throws IOException {
        String rubric = readBounded(rubricPath);
        LoadSettings settings = LoadSettings.builder()
                .setLabel(personasPath.toString())
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(10)
                .build();
        Object loaded = new Load(settings).loadFromString(readBounded(personasPath));
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("personas.yml must contain a mapping");
        }
        requireExactKeys(root, Set.of("min_judges", "personas"), "personas.yml");
        int minJudges = requireInteger(root.get("min_judges"), "min_judges");
        if (!(root.get("personas") instanceof List<?> entries)) {
            throw new IllegalArgumentException("personas must be a YAML list");
        }
        List<Persona> personas = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Object value = entries.get(index);
            if (!(value instanceof Map<?, ?> persona)) {
                throw new IllegalArgumentException("persona " + index + " must be a mapping");
            }
            requireExactKeys(persona, Set.of("name", "voice"), "persona " + index);
            String name = requireString(
                    persona.get("name"), "persona name", MAX_PERSONA_NAME_LENGTH);
            requireSafeText(name, "persona name");
            personas.add(new Persona(
                    name,
                    requireString(
                            persona.get("voice"), "persona voice", MAX_PERSONA_VOICE_LENGTH)));
        }
        return new JudgeConfig(minJudges, personas, rubric);
    }

    private static void requireExactKeys(Map<?, ?> value, Set<String> expected, String label) {
        Set<String> actual = value.keySet().stream()
                .map(key -> key instanceof String text ? text : "<non-string>")
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    label + " must contain exactly these keys: " + expected);
        }
    }

    private static int requireInteger(Object value, String label) {
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        return integer;
    }

    private static String requireString(Object value, String label, int maxLength) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be a non-blank string");
        }
        return text;
    }

    private static String readBounded(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
        }
        if (bytes.length > MAX_CONFIG_BYTES) {
            throw new IOException(path.getFileName() + " exceeds the byte limit");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireSafeText(String value, String label) {
        if (value.codePoints().anyMatch(JudgeConfig::isUnsafeTextCodePoint)) {
            throw new IllegalArgumentException(label + " contains unsafe control characters");
        }
    }

    private static boolean isUnsafeTextCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
