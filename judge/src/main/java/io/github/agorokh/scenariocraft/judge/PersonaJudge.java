package io.github.agorokh.scenariocraft.judge;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
interface PersonaJudge {
    JudgeVerdict judge(
            Persona persona, String task, String rubric, String plotId, List<Path> images)
            throws JudgeException;
}
