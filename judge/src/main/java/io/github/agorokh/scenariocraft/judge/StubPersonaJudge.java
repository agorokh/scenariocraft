package io.github.agorokh.scenariocraft.judge;

import java.nio.file.Path;
import java.util.List;

final class StubPersonaJudge implements PersonaJudge {
    @Override
    public JudgeVerdict judge(
            Persona persona, String task, String rubric, String plotId, List<Path> images) {
        int base = "p1".equals(plotId) ? 8 : 6;
        int adjustment = Math.floorMod(persona.name().hashCode(), 3) - 1;
        int score = Math.max(1, Math.min(10, base + adjustment));
        return new JudgeVerdict(
                persona.name(),
                "The build follows the task and shows a deliberate shape, materials, and details.",
                new Scores(score, score, Math.min(10, score + 1), score),
                "Your build has a clear idea and a genuine detail worth celebrating. "
                        + "Keep experimenting with texture to make the next version even stronger.");
    }
}
