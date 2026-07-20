package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAiPersonaJudgeTest {
    private static final Persona PERSONA = new Persona("Professor Fixture", "Kind and precise.");
    private static final String RUBRIC =
            "theme_fit creativity effort detail; always name one genuine strength.";

    @TempDir
    Path temporaryDirectory;

    @Test
    void requestUsesSevenImagesSharedRubricAndStrictReasonThenScoresSchema() throws Exception {
        List<Path> images = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            Path image = temporaryDirectory.resolve(index + ".png");
            Files.write(image, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, (byte) index});
            images.add(image);
        }

        String body = OpenAiPersonaJudge.requestBody(
                PERSONA, "Build a cottage", RUBRIC, "p1", images);
        JsonObject request = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("gpt-5.6", request.get("model").getAsString());
        assertFalse(body.contains("temperature"));
        JsonArray input = request.getAsJsonArray("input");
        String sharedInstructions = input.get(0).getAsJsonObject().getAsJsonArray("content")
                .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(sharedInstructions.contains("Name one genuine strength"));
        assertEquals(RUBRIC, input.get(0).getAsJsonObject().getAsJsonArray("content")
                .get(1).getAsJsonObject().get("text").getAsString());
        assertEquals(8, input.get(1).getAsJsonObject().getAsJsonArray("content").size());
        JsonObject format = request.getAsJsonObject("text").getAsJsonObject("format");
        assertTrue(format.get("strict").getAsBoolean());
        List<String> propertyOrder = new ArrayList<>(format.getAsJsonObject("schema")
                .getAsJsonObject("properties").keySet());
        assertEquals(List.of("persona", "reasoning", "scores", "comment"), propertyOrder);
        assertTrue(body.indexOf("\"reasoning\"") < body.indexOf("\"scores\""));
    }

    @Test
    void parsesValidStructuredVerdictFromResponseEnvelope() throws Exception {
        String verdict = validVerdict();
        JsonObject text = new JsonObject();
        text.addProperty("type", "output_text");
        text.addProperty("text", verdict);
        JsonArray content = new JsonArray();
        content.add(text);
        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.add("content", content);
        JsonArray output = new JsonArray();
        output.add(message);
        JsonObject response = new JsonObject();
        response.add("output", output);

        JudgeVerdict parsed = OpenAiPersonaJudge.parseResponse(
                response.toString(), PERSONA.name());

        assertEquals(PERSONA.name(), parsed.persona());
        assertEquals(7.5, parsed.score());
    }

    @Test
    void rejectsMalformedOrOutOfRangeResponsesForRetry() {
        assertThrows(JudgeException.class,
                () -> OpenAiPersonaJudge.parseVerdict("not json", PERSONA.name()));
        assertThrows(JudgeException.class,
                () -> OpenAiPersonaJudge.parseVerdict(validVerdict().replace(
                        "\"detail\":9", "\"detail\":11"), PERSONA.name()));
        assertThrows(JudgeException.class,
                () -> OpenAiPersonaJudge.parseVerdict(validVerdict().replace(
                        ",\"detail\":9", ""), PERSONA.name()));
        assertThrows(JudgeException.class,
                () -> OpenAiPersonaJudge.parseVerdict(validVerdict().replace(
                        "Your doorway is welcoming. Add roof texture next.",
                        "Your doorway is welcoming."), PERSONA.name()));
    }

    private String validVerdict() {
        return """
                {"persona":"Professor Fixture","reasoning":"The cottage fits the task.",
                "scores":{"theme_fit":7,"creativity":8,"effort":6,"detail":9},
                "comment":"Your doorway is welcoming. Add roof texture next."}
                """;
    }
}
