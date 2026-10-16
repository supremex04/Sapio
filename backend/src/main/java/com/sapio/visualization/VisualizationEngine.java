package com.sapio.visualization;

import java.util.Locale;

/**
 * How a visualization is shown: an animated video (Manim code), a molecule (RDKit, 3D viewer), a structure from the
 * Protein Data Bank (3D viewer) or a program traced line by line. {@code AUTO}: chosen by the rules, then by the
 * storyboard (docs/adr/0017-visualization-engines.md).
 */
public enum VisualizationEngine {
    AUTO, ANIMATION, MOLECULE, PROTEIN, CODE;

    /** Name on the wire to inference (lowercase). */
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    static VisualizationEngine fromWire(String value) {
        return value == null ? ANIMATION : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
