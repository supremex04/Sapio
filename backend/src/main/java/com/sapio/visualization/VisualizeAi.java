package com.sapio.visualization;

import java.util.List;

import com.sapio.study.ai.StudyAi;

/** The AI half of a visualization (inference /v1/visualize): plan it, then write (or repair) the Manim scene. */
public interface VisualizeAi {

    /** {@code focus}: what to point at, per engine (SMARTS, chain:residues or ligand:XXX, code lines). */
    record Beat(String shows, String says, String focus) {
    }

    /** {@code engine}: auto (the storyboard picks), or the engine to use (lowercase). */
    record StoryboardSpec(String request, String language, String context, StudyAi.Learner learner, String engine) {
    }

    record Molecule(String name, String smiles) {
    }

    record Protein(String pdbId, String name) {
    }

    /** One call plans and routes: the engine, and its data (molecule, protein or program) when it needs some. */
    record Storyboard(boolean feasible, String reason, String title, String kind, String engine, Molecule molecule,
                      Protein protein, String program, List<Beat> beats, String model) {
    }

    /** {@code previousCode} and {@code error}: a repair of code the renderer could not run. */
    record CodeSpec(String title, List<Beat> beats, String previousCode, String error) {
    }

    record SceneCode(String code, String model) {
    }

    Storyboard storyboard(StoryboardSpec spec) throws VisualizationException;

    SceneCode code(CodeSpec spec) throws VisualizationException;
}
