package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.zip.CRC32;
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
        List<JudgeImage> images = new ArrayList<>();
        for (String name : RoundImages.NAMES) {
            Path image = temporaryDirectory.resolve(name);
            Files.write(image, completePng(640, 480));
            images.add(JudgeImage.read(image, temporaryDirectory.toRealPath()));
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
        assertTrue(sharedInstructions.contains("Start sentence two with Try, Next, or Consider"));
        assertTrue(sharedInstructions.contains("center cross-section views"));
        assertEquals(RUBRIC, input.get(0).getAsJsonObject().getAsJsonArray("content")
                .get(1).getAsJsonObject().get("text").getAsString());
        JsonArray userContent = input.get(1).getAsJsonObject().getAsJsonArray("content");
        assertEquals(15, userContent.size());
        for (int index = 0; index < RoundImages.NAMES.size(); index++) {
            JsonObject label = userContent.get(1 + index * 2).getAsJsonObject();
            JsonObject image = userContent.get(2 + index * 2).getAsJsonObject();
            assertEquals("input_text", label.get("type").getAsString());
            assertTrue(label.get("text").getAsString().contains(RoundImages.NAMES.get(index)));
            assertEquals("input_image", image.get("type").getAsString());
        }
        assertTrue(userContent.get(11).getAsJsonObject().get("text").getAsString()
                .contains("center X cross-section showing interior evidence"));
        assertTrue(userContent.get(13).getAsJsonObject().get("text").getAsString()
                .contains("center Z cross-section showing interior evidence"));
        JsonObject format = request.getAsJsonObject("text").getAsJsonObject("format");
        assertTrue(format.get("strict").getAsBoolean());
        List<String> propertyOrder = new ArrayList<>(format.getAsJsonObject("schema")
                .getAsJsonObject("properties").keySet());
        assertEquals(List.of("persona", "reasoning", "scores", "comment"), propertyOrder);
        assertTrue(
                format.getAsJsonObject("schema")
                        .getAsJsonObject("properties")
                        .getAsJsonObject("comment")
                        .get("description")
                        .getAsString()
                        .contains("visible build feature"));
        assertTrue(body.indexOf("\"reasoning\"") < body.indexOf("\"scores\""));
    }

    @Test
    void rejectsAnIncompleteCanonicalImageSet() throws Exception {
        Path image = temporaryDirectory.resolve(RoundImages.NAMES.getFirst());
        Files.write(image, completePng(640, 480));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenAiPersonaJudge.requestBody(
                        PERSONA,
                        "Build a cottage",
                        RUBRIC,
                        "p1",
                        List.of(JudgeImage.read(image, temporaryDirectory.toRealPath()))));

        assertTrue(exception.getMessage().contains("complete canonical image set"));
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
        response.addProperty("status", "completed");
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
        assertThrows(JudgeException.class,
                () -> OpenAiPersonaJudge.parseVerdict(validVerdict().replace(
                        "Your doorway is welcoming. Add roof texture next.",
                        "Your doorway is ugly. Add roof texture next."), PERSONA.name()));
    }

    @Test
    void rejectsNonCompletedResponseEvenWhenItContainsVerdictText() {
        String response = """
                {"status":"incomplete","output":[{"content":[
                  {"type":"output_text","text":%s}
                ]}]}
                """.formatted(new com.google.gson.Gson().toJson(validVerdict()));

        JudgeException exception = assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseResponse(response, PERSONA.name()));

        assertTrue(exception.getMessage().contains("was not completed"));
    }

    @Test
    void rejectsLaterRefusalOrMultipleVerdictsInsteadOfAcceptingTheFirst() {
        String responseWithRefusal = """
                {"status":"completed","output":[
                  {"content":[{"type":"output_text","text":%s}]},
                  {"content":[{"type":"refusal","refusal":"unsafe"}]}
                ]}
                """.formatted(new com.google.gson.Gson().toJson(validVerdict()));
        String responseWithTwoVerdicts = """
                {"status":"completed","output":[
                  {"content":[{"type":"output_text","text":%s}]},
                  {"content":[{"type":"output_text","text":%s}]}
                ]}
                """.formatted(
                        new com.google.gson.Gson().toJson(validVerdict()),
                        new com.google.gson.Gson().toJson(validVerdict()));
        String responseWithTopLevelRefusal = """
                {"status":"completed","output":[
                  {"content":[{"type":"output_text","text":%s}]},
                  {"type":"refusal","refusal":"unsafe"}
                ]}
                """.formatted(new com.google.gson.Gson().toJson(validVerdict()));

        assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseResponse(responseWithRefusal, PERSONA.name()));
        assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseResponse(responseWithTwoVerdicts, PERSONA.name()));
        assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseResponse(
                        responseWithTopLevelRefusal, PERSONA.name()));
    }

    @Test
    void sanitizesUsefulApiErrorDetailsWithoutEchoingSecrets() {
        String message = OpenAiPersonaJudge.httpErrorMessage(
                429,
                "{\"error\":{\"message\":\"Quota exhausted for key-fixture_redaction\"}}",
                "req_123",
                "key-fixture_redaction");

        assertTrue(message.contains("Quota exhausted"));
        assertTrue(message.contains("request req_123"));
        assertFalse(message.contains("key-fixture_redaction"));
        assertTrue(OpenAiPersonaJudge.isRetryableHttpStatus(429));
        assertTrue(OpenAiPersonaJudge.isRetryableHttpStatus(503));
        assertFalse(OpenAiPersonaJudge.isRetryableHttpStatus(401));
    }

    @Test
    void createsModerationRequestAndFailsClosedOnMalformedResults() throws Exception {
        JsonObject request = JsonParser.parseString(
                OpenAiPersonaJudge.moderationRequestBody(
                        "Your doorway is welcoming. Add roof texture next."))
                .getAsJsonObject();

        assertEquals("omni-moderation-latest", request.get("model").getAsString());
        assertEquals(
                "Your doorway is welcoming. Add roof texture next.",
                request.get("input").getAsString());
        assertFalse(OpenAiPersonaJudge.parseModerationFlag(
                "{\"results\":[{\"flagged\":false}]}"));
        assertTrue(OpenAiPersonaJudge.parseModerationFlag(
                "{\"results\":[{\"flagged\":true}]}"));
        assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseModerationFlag("{\"results\":[]}"));
        assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.parseModerationFlag(
                        "{\"results\":[{\"categories\":{}}]}"));
    }

    @Test
    void retriesModerationWithoutRegeneratingTheVerdict() throws Exception {
        AtomicInteger moderationCalls = new AtomicInteger();

        OpenAiPersonaJudge.moderateText("safe verdict", body -> {
            if (moderationCalls.incrementAndGet() == 1) {
                throw new JudgeException("temporary moderation failure");
            }
            return "{\"results\":[{\"flagged\":false}]}";
        });

        assertEquals(2, moderationCalls.get());
    }

    @Test
    void moderationExhaustionCannotTriggerAnotherVisionRequest() {
        AtomicInteger moderationCalls = new AtomicInteger();

        JudgeException exception = assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.moderateText("safe verdict", body -> {
                    moderationCalls.incrementAndGet();
                    throw new IOException("temporary moderation failure");
                }));

        assertFalse(exception.retryable());
        assertEquals(2, moderationCalls.get());
    }

    @Test
    void doesNotRetryAFlaggedVerdict() {
        AtomicInteger moderationCalls = new AtomicInteger();

        JudgeException exception = assertThrows(
                JudgeException.class,
                () -> OpenAiPersonaJudge.moderateText("unsafe verdict", body -> {
                    moderationCalls.incrementAndGet();
                    return "{\"results\":[{\"flagged\":true}]}";
                }));

        assertFalse(exception.retryable());
        assertEquals(1, moderationCalls.get());
    }

    @Test
    void rejectsOpenAiResponseBodiesAboveTheHeapBound() {
        var subscriber = OpenAiPersonaJudge.boundedBodySubscriber();
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long count) {}

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });

        assertThrows(
                CompletionException.class,
                () -> {
                    subscriber.onNext(List.of(ByteBuffer.allocate(
                            OpenAiPersonaJudge.MAX_RESPONSE_BYTES + 1)));
                    subscriber.getBody().toCompletableFuture().join();
                });
        assertTrue(cancelled.get());
    }

    @Test
    void rejectsOversizedImagesBeforeReadingThemIntoMemory() throws Exception {
        Path oversized = temporaryDirectory.resolve("oversized.png");
        try (FileChannel channel = FileChannel.open(
                oversized, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(JudgeImage.MAX_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }

        assertThrows(
                IOException.class,
                () -> JudgeImage.read(oversized, temporaryDirectory.toRealPath()));
    }

    @Test
    void rejectsPngDimensionsAboveTheUploadLimit() throws Exception {
        Path hugeDimensions = temporaryDirectory.resolve("huge-dimensions.png");
        Files.write(hugeDimensions, completePng(JudgeImage.MAX_DIMENSION + 1, 1));

        assertThrows(
                IOException.class,
                () -> JudgeImage.read(hugeDimensions, temporaryDirectory.toRealPath()));
    }

    @Test
    void rejectsDecodedPixelCountsAboveTheHeapBudget() throws Exception {
        Path tooManyPixels = temporaryDirectory.resolve("too-many-pixels.png");
        Files.write(tooManyPixels, structuralPng(2049, 2049));

        assertThrows(
                IOException.class,
                () -> JudgeImage.read(tooManyPixels, temporaryDirectory.toRealPath()));
    }

    @Test
    void rejectsTruncatedPngHeaderWithoutImageDataOrEndChunk() throws Exception {
        Path truncated = temporaryDirectory.resolve("truncated.png");
        Files.write(truncated, ByteBuffer.allocate(24)
                .put(new byte[] {
                    (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
                })
                .putInt(13)
                .put(new byte[] {'I', 'H', 'D', 'R'})
                .putInt(1)
                .putInt(1)
                .array());

        assertThrows(
                IOException.class,
                () -> JudgeImage.read(truncated, temporaryDirectory.toRealPath()));
    }

    @Test
    void rejectsCrcValidChunksWithUndecodableImageData() throws Exception {
        Path invalidRaster = temporaryDirectory.resolve("invalid-raster.png");
        Files.write(invalidRaster, structuralPng(1, 1));

        assertThrows(
                IOException.class,
                () -> JudgeImage.read(invalidRaster, temporaryDirectory.toRealPath()));
    }

    @Test
    void rejectsPathologicalPngChunkCountsBeforeRasterDecode() throws Exception {
        Path chunkFlood = temporaryDirectory.resolve("chunk-flood.png");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
            });
            byte[] header = ByteBuffer.allocate(13)
                    .putInt(1).putInt(1)
                    .put((byte) 8).put((byte) 2)
                    .put((byte) 0).put((byte) 0).put((byte) 0)
                    .array();
            writeChunk(output, "IHDR", header);
            for (int index = 0; index < JudgeImage.MAX_CHUNKS; index++) {
                writeChunk(output, "tEXt", new byte[0]);
            }
        }
        Files.write(chunkFlood, bytes.toByteArray());

        IOException exception = assertThrows(
                IOException.class,
                () -> JudgeImage.read(chunkFlood, temporaryDirectory.toRealPath()));

        assertTrue(exception.getMessage().contains("too many PNG chunks"));
    }

    private String validVerdict() {
        return """
                {"persona":"Professor Fixture","reasoning":"The cottage fits the task.",
                "scores":{"theme_fit":7,"creativity":8,"effort":6,"detail":9},
                "comment":"Your doorway is welcoming. Add roof texture next."}
                """;
    }

    private byte[] completePng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("PNG writer is unavailable");
        }
        return bytes.toByteArray();
    }

    private byte[] structuralPng(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
            });
            byte[] header = ByteBuffer.allocate(13)
                    .putInt(width)
                    .putInt(height)
                    .put((byte) 8)
                    .put((byte) 2)
                    .put((byte) 0)
                    .put((byte) 0)
                    .put((byte) 0)
                    .array();
            writeChunk(output, "IHDR", header);
            writeChunk(output, "IDAT", new byte[] {0x78, 0x01, 0x01});
            writeChunk(output, "IEND", new byte[0]);
        }
        return bytes.toByteArray();
    }

    private void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 checksum = new CRC32();
        checksum.update(typeBytes);
        checksum.update(data);
        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        output.writeInt((int) checksum.getValue());
    }
}
