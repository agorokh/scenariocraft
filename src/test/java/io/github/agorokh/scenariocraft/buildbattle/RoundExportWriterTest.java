package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundExportWriterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void normativeWorkedExampleRoundTripsExactly() throws IOException {
        VoxelFile fixture;
        try (Reader reader = resource("fixtures/schema-v1/p1.voxels.json")) {
            fixture = RoundExportWriter.json().fromJson(reader, VoxelFile.class);
        }
        List<String> knownStructure =
                List.of(
                        "minecraft:air",
                        "minecraft:oak_planks",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air");
        RoundExportRequest.Plot metadata =
                new RoundExportRequest.Plot("p1", "KidAva", 100, 64, 200, 2, 2, 2);

        VoxelFile encoded =
                VoxelCodec.encode(new RoundSnapshot.Plot(metadata, knownStructure));

        assertEquals(fixture, encoded);
        assertEquals(knownStructure, VoxelCodec.decode(encoded));
        assertEquals(8, encoded.blocks().size());
        assertEquals(1, encoded.blocks().get(1));
    }

    @Test
    void completeRoundFilesExistAndMatchFrozenJsonSchemas() throws IOException {
        List<String> knownStructure =
                List.of(
                        "minecraft:air",
                        "minecraft:oak_planks",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:air",
                        "minecraft:oak_planks");
        RoundExportRequest.Plot metadata =
                new RoundExportRequest.Plot("p1", "KidAva", 100, 64, 200, 2, 2, 2);
        RoundSnapshot snapshot =
                new RoundSnapshot(
                        "round-20260720-204209",
                        "Pirate ship",
                        "battle_world",
                        List.of(new RoundSnapshot.Plot(metadata, knownStructure)));

        Path roundDirectory =
                new RoundExportWriter(temporaryDirectory.resolve("rounds")).write(snapshot);
        Path manifestPath = roundDirectory.resolve("manifest.json");
        Path voxelPath = roundDirectory.resolve("p1.voxels.json");

        assertTrue(Files.isRegularFile(manifestPath));
        assertTrue(Files.isRegularFile(voxelPath));
        JsonSchemaAssertions.assertMatches(
                JsonParser.parseString(Files.readString(manifestPath)),
                schema("fixtures/schema-v1/manifest.schema.json"));
        JsonSchemaAssertions.assertMatches(
                JsonParser.parseString(Files.readString(voxelPath)),
                schema("fixtures/schema-v1/voxels.schema.json"));
        VoxelFile voxels =
                RoundExportWriter.json()
                        .fromJson(Files.readString(voxelPath), VoxelFile.class);
        assertEquals(knownStructure, VoxelCodec.decode(voxels));
    }

    @Test
    void entirelyAirPlotIsStillWrittenAndListedWithZeroHeight() throws IOException {
        RoundExportRequest.Plot metadata =
                new RoundExportRequest.Plot("p1", "KidAva", 100, 64, 200, 2, 2, 2);
        RoundSnapshot.Plot emptyPlot =
                new RoundSnapshot.Plot(
                        metadata,
                        new ArrayList<>(
                                java.util.Collections.nCopies(8, "minecraft:air")));
        RoundSnapshot snapshot =
                new RoundSnapshot(
                        "round-20260720-204209",
                        "Pirate ship",
                        "battle_world",
                        List.of(emptyPlot));

        Path roundDirectory =
                new RoundExportWriter(temporaryDirectory.resolve("rounds")).write(snapshot);
        VoxelFile voxels =
                RoundExportWriter.json()
                        .fromJson(
                                Files.readString(roundDirectory.resolve("p1.voxels.json")),
                                VoxelFile.class);
        RoundManifest manifest =
                RoundExportWriter.json()
                        .fromJson(
                                Files.readString(roundDirectory.resolve("manifest.json")),
                                RoundManifest.class);

        assertEquals(List.of(2, 0, 2), voxels.size());
        assertEquals(List.of("minecraft:air"), voxels.palette());
        assertTrue(voxels.blocks().isEmpty());
        assertEquals(1, manifest.plots().size());
        assertEquals(List.of(2, 0, 2), manifest.plots().getFirst().size());
    }

    private static JsonObject schema(String name) throws IOException {
        try (Reader reader = resource(name)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Reader resource(String name) {
        return new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(
                        RoundExportWriterTest.class.getClassLoader().getResourceAsStream(name),
                        name),
                StandardCharsets.UTF_8);
    }
}
