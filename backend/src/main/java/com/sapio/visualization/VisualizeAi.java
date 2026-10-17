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
    /** A grid node; {@code ground}: the 0 V reference. */
    record CircuitNode(String id, int x, int y, boolean ground) {
    }

    /** A part between two nodes; {@code value} in ohms, farads, henries, volts or amperes (null for a wire). */
    record CircuitPart(String id, String kind, Double value, String a, String b) {
    }

    record Circuit(List<CircuitNode> nodes, List<CircuitPart> parts) {
    }

    record Storyboard(boolean feasible, String reason, String title, String kind, String engine, Molecule molecule,
                      Protein protein, Circuit circuit, String program, List<Beat> beats, String model) {
    }

    /** {@code previousCode} and {@code error}: a repair of code the renderer could not run. */
    record CodeSpec(String title, List<Beat> beats, String previousCode, String error) {
    }

    record SceneCode(String code, String model) {
    }

    /** A circuit the renderer refused, with its problems, to be fixed for the same storyboard. */
    record CircuitRepairSpec(String request, String title, List<Beat> beats, Circuit circuit, List<String> problems) {
    }

    record CircuitRepair(Circuit circuit, String model) {
    }

    Storyboard storyboard(StoryboardSpec spec) throws VisualizationException;

    SceneCode code(CodeSpec spec) throws VisualizationException;

    CircuitRepair repairCircuit(CircuitRepairSpec spec) throws VisualizationException;
}
