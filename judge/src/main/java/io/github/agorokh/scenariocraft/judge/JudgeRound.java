package io.github.agorokh.scenariocraft.judge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record JudgeRound(int schema, String roundId, String task, String world, List<Plot> plots) {
    static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    static final int MAX_PLOTS = 8;
    private static final int MAX_PLOT_DIMENSION = 256;
    private static final int MAX_PLOT_VOLUME = 1_000_000;
    private static final int MAX_TASK_LENGTH = 512;
    private static final int MAX_WORLD_LENGTH = 128;
    private static final int MAX_PLAYER_LENGTH = 64;
    private static final Set<String> ROOT_KEYS =
            Set.of("schema", "round_id", "task", "world", "plots");
    private static final Set<String> PLOT_KEYS =
            Set.of("plot_id", "player", "origin", "size");

    JudgeRound {
        plots = List.copyOf(plots);
        if (schema != 1) {
            throw new IllegalArgumentException("manifest schema must be 1");
        }
        if (roundId == null || !roundId.matches("round-[0-9]{8}-[0-9]{6}")) {
            throw new IllegalArgumentException("round_id has an invalid format");
        }
        if (task == null || task.isBlank() || task.length() > MAX_TASK_LENGTH
                || world == null || world.isBlank() || world.length() > MAX_WORLD_LENGTH) {
            throw new IllegalArgumentException("manifest task and world must be non-blank");
        }
        requireSafeText(task, "manifest task");
        requireSafeText(world, "manifest world");
        if (plots.isEmpty() || plots.size() > MAX_PLOTS) {
            throw new IllegalArgumentException(
                    "manifest must contain from 1 to " + MAX_PLOTS + " plots");
        }
        Set<String> ids = new HashSet<>();
        for (Plot plot : plots) {
            if (!ids.add(plot.plotId())) {
                throw new IllegalArgumentException("manifest plot_id values must be unique");
            }
        }
    }

    static JudgeRound read(Path path) throws IOException {
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            }
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw new IOException("manifest.json exceeds the byte limit");
            }
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("manifest.json must contain an object");
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.keySet().equals(ROOT_KEYS)) {
                throw new IllegalArgumentException(
                        "manifest.json has missing or unexpected fields");
            }
            int schema = requireInteger(object, "schema");
            String roundId = requireString(object, "round_id");
            String task = requireString(object, "task");
            String world = requireString(object, "world");
            JsonArray plotValues = requireArray(object, "plots");
            List<Plot> plots = new ArrayList<>();
            for (JsonElement value : plotValues) {
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("manifest plots must contain objects");
                }
                JsonObject plot = value.getAsJsonObject();
                if (!plot.keySet().equals(PLOT_KEYS)) {
                    throw new IllegalArgumentException(
                            "manifest plot has missing or unexpected fields");
                }
                plots.add(new Plot(
                        requireString(plot, "plot_id"),
                        requireString(plot, "player"),
                        requireIntegerArray(plot, "origin"),
                        requireIntegerArray(plot, "size")));
            }
            return new JudgeRound(schema, roundId, task, world, plots);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid manifest JSON: " + exception.getMessage(), exception);
        }
    }

    private static int requireInteger(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        String token = primitive.getAsString();
        if (!primitive.isNumber() || !token.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must fit in a 32-bit integer", exception);
        }
    }

    private static String requireString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static JsonArray requireArray(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be a JSON array");
        }
        return value.getAsJsonArray();
    }

    private static List<Integer> requireIntegerArray(JsonObject object, String name) {
        JsonArray values = requireArray(object, name);
        if (values.size() != 3) {
            throw new IllegalArgumentException(name + " must contain exactly three integers");
        }
        List<Integer> result = new ArrayList<>(3);
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive()) {
                throw new IllegalArgumentException(name + " must contain JSON integers");
            }
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            String token = primitive.getAsString();
            if (!primitive.isNumber() || !token.matches("-?(0|[1-9][0-9]*)")) {
                throw new IllegalArgumentException(name + " must contain JSON integers");
            }
            try {
                result.add(Integer.parseInt(token));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        name + " values must fit in a 32-bit integer", exception);
            }
        }
        return List.copyOf(result);
    }

    private static void requireSafeText(String value, String name) {
        if (value.codePoints().anyMatch(JudgeRound::isUnsafeTextCodePoint)) {
            throw new IllegalArgumentException(name + " contains unsafe control characters");
        }
    }

    private static boolean isUnsafeTextCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    record Plot(String plotId, String player, List<Integer> origin, List<Integer> size) {
        Plot {
            if (plotId == null || !plotId.matches("p[1-9][0-9]*")) {
                throw new IllegalArgumentException("plot_id has an unsafe format");
            }
            if (player == null || player.isBlank() || player.length() > MAX_PLAYER_LENGTH) {
                throw new IllegalArgumentException("player must be non-blank");
            }
            requireSafeText(player, "player");
            origin = List.copyOf(origin);
            size = List.copyOf(size);
            if (origin.size() != 3 || size.size() != 3) {
                throw new IllegalArgumentException(
                        "manifest plot origin and size must contain three integers");
            }
            if (size.stream().anyMatch(value -> value < 0 || value > MAX_PLOT_DIMENSION)) {
                throw new IllegalArgumentException(
                        "manifest plot size is outside the supported range");
            }
            long volume = (long) size.get(0) * size.get(1) * size.get(2);
            if (volume > MAX_PLOT_VOLUME) {
                throw new IllegalArgumentException("manifest plot volume exceeds the limit");
            }
        }
    }
}
