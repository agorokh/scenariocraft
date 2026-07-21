package io.github.agorokh.scenariocraft.judge;

import java.util.List;

@FunctionalInterface
interface PersonaJudge {
    JudgeVerdict judge(
            Persona persona, String task, String rubric, String plotId, List<JudgeImage> images)
            throws JudgeException;
}
