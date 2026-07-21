package io.github.agorokh.scenariocraft.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JudgeConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fixturePersonasCarryOnlyNameAndVoice() throws Exception {
        JudgeConfig config = JudgeConfig.load(personasFixture(), rubricFixture());

        assertEquals(2, config.minJudges());
        assertEquals(2, config.personas().size());
    }

    @Test
    void rejectsRubricOrWeightsInsidePersona() throws Exception {
        Path personas = temporaryDirectory.resolve("personas.yml");
        Files.writeString(personas, """
                min_judges: 2
                personas:
                  - name: One
                    voice: Kind
                    criteria: [detail]
                  - name: Two
                    voice: Warm
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> JudgeConfig.load(personas, rubricFixture()));
    }

    @Test
    void fixtureRubricNamesEveryFrozenCriterion() throws Exception {
        String rubric = Files.readString(rubricFixture());

        for (String criterion : JudgeConfig.CRITERIA) {
            assertTrue(rubric.contains(criterion), criterion);
        }
        JudgeConfig.load(personasFixture(), rubricFixture());
    }

    @Test
    void productionPersonasAndRubricHonorFrozenContract() throws Exception {
        Path productionRoot = Path.of(System.getProperty("scenariocraft.repoRoot"))
                .resolve("judge");

        JudgeConfig config = JudgeConfig.load(
                productionRoot.resolve("personas.yml"),
                productionRoot.resolve("rubric.md"));

        assertEquals(3, config.personas().size());
        for (String criterion : JudgeConfig.CRITERIA) {
            assertTrue(config.rubric().contains(criterion), criterion);
        }
    }

    @Test
    void rejectsConfigurationFilesAboveThePromptBound() throws Exception {
        Path oversized = temporaryDirectory.resolve("rubric.md");
        try (FileChannel channel = FileChannel.open(
                oversized, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(JudgeConfig.MAX_CONFIG_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }

        assertThrows(
                java.io.IOException.class,
                () -> JudgeConfig.load(personasFixture(), oversized));
    }

    private Path personasFixture() {
        return fixtureRoot().resolve("judge/personas.yml");
    }

    private Path rubricFixture() {
        return fixtureRoot().resolve("judge/rubric.md");
    }

    private Path fixtureRoot() {
        return Path.of(System.getProperty("scenariocraft.repoRoot"))
                .resolve("judge/src/test/resources/fixtures/runtime");
    }
}
