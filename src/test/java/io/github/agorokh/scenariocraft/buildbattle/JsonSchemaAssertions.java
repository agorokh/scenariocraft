package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.regex.Pattern;

/** Tiny validator for the JSON-schema keywords used by the frozen contract fixtures. */
final class JsonSchemaAssertions {
    private JsonSchemaAssertions() {}

    static void assertMatches(JsonElement instance, JsonObject schema) {
        assertMatches(instance, schema, "$", true);
    }

    private static void assertMatches(
            JsonElement instance, JsonObject schema, String path, boolean root) {
        assertNotNull(instance, path + " must exist");
        String type = schema.get("type").getAsString();
        switch (type) {
            case "object" -> assertObject(instance, schema, path);
            case "array" -> assertArray(instance, schema, path);
            case "string" -> assertString(instance, schema, path);
            case "integer" -> assertInteger(instance, schema, path);
            default -> throw new AssertionError("Unsupported schema type " + type);
        }
        if (schema.has("const")) {
            assertEquals(schema.get("const"), instance, path + " must equal its const value");
        }
        assertTrue(root || !path.isBlank());
    }

    private static void assertObject(JsonElement instance, JsonObject schema, String path) {
        assertTrue(instance.isJsonObject(), path + " must be an object");
        JsonObject object = instance.getAsJsonObject();
        JsonObject properties = schema.getAsJsonObject("properties");
        for (JsonElement required : schema.getAsJsonArray("required")) {
            assertTrue(
                    object.has(required.getAsString()),
                    path + " must contain " + required.getAsString());
        }
        if (schema.has("additionalProperties")
                && !schema.get("additionalProperties").getAsBoolean()) {
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                assertTrue(
                        properties.has(field.getKey()),
                        path + " contains unexpected field " + field.getKey());
            }
        }
        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            if (object.has(property.getKey())) {
                assertMatches(
                        object.get(property.getKey()),
                        property.getValue().getAsJsonObject(),
                        path + "." + property.getKey(),
                        false);
            }
        }
    }

    private static void assertArray(JsonElement instance, JsonObject schema, String path) {
        assertTrue(instance.isJsonArray(), path + " must be an array");
        int size = instance.getAsJsonArray().size();
        if (schema.has("minItems")) {
            assertTrue(size >= schema.get("minItems").getAsInt(), path + " is too short");
        }
        if (schema.has("maxItems")) {
            assertTrue(size <= schema.get("maxItems").getAsInt(), path + " is too long");
        }
        if (schema.has("items")) {
            JsonObject itemSchema = schema.getAsJsonObject("items");
            for (int index = 0; index < size; index++) {
                assertMatches(
                        instance.getAsJsonArray().get(index),
                        itemSchema,
                        path + "[" + index + "]",
                        false);
            }
        }
    }

    private static void assertString(JsonElement instance, JsonObject schema, String path) {
        assertTrue(instance.isJsonPrimitive(), path + " must be a string");
        assertTrue(instance.getAsJsonPrimitive().isString(), path + " must be a string");
        String value = instance.getAsString();
        if (schema.has("minLength")) {
            assertTrue(
                    value.length() >= schema.get("minLength").getAsInt(),
                    path + " is too short");
        }
        if (schema.has("pattern")) {
            assertTrue(
                    Pattern.compile(schema.get("pattern").getAsString())
                            .matcher(value)
                            .matches(),
                    path + " does not match its pattern");
        }
    }

    private static void assertInteger(JsonElement instance, JsonObject schema, String path) {
        assertTrue(instance.isJsonPrimitive(), path + " must be an integer");
        assertTrue(instance.getAsJsonPrimitive().isNumber(), path + " must be an integer");
        double decimal = instance.getAsDouble();
        assertFalse(Double.isNaN(decimal), path + " must be finite");
        assertEquals(Math.rint(decimal), decimal, path + " must be an integer");
    }
}
