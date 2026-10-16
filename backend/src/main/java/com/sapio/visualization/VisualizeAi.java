package com.sapio.visualization;

import java.util.List;

import com.sapio.study.ai.StudyAi;

/** The AI half of a visualization (inference /v1/visualize): plan it, then write (or repair) the Manim scene. */
public interface VisualizeAi {

    record Beat(String shows, String says) {
    }

    record StoryboardSpec(String request, String language, String context, StudyAi.Learner learner) {
    }

    record Storyboard(boolean feasible, String reason, String title, String kind, List<Beat> beats, String model) {
    }

    /** {@code previousCode} and {@code error}: a repair of code the renderer could not run. */
    record CodeSpec(String title, List<Beat> beats, String previousCode, String error) {
    }

    record SceneCode(String code, String model) {
    }

    Storyboard storyboard(StoryboardSpec spec) throws VisualizationException;

    SceneCode code(CodeSpec spec) throws VisualizationException;
}
