package com.sapio.visualization;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRouterTest {

    private static Optional<VisualizationEngine> engine(String request) {
        return EngineRouter.route(request).map(EngineRouter.Routed::engine);
    }

    @Test
    void pastedCodeGoesToTheTracerWithItsFenceRemoved() {
        String code = "```python\ndef fact(n):\n    if n <= 1:\n        return 1\n    return n * fact(n - 1)\n```";
        EngineRouter.Routed routed = EngineRouter.route(code).orElseThrow();
        assertThat(routed.engine()).isEqualTo(VisualizationEngine.CODE);
        assertThat(routed.data()).startsWith("def fact(n):").doesNotContain("```");
        assertThat(engine("for i in range(3):\n    print(i)")).contains(VisualizationEngine.CODE);
        assertThat(engine("int x = 0;\nx++;\nprintf(\"%d\", x);")).contains(VisualizationEngine.CODE);
    }

    @Test
    void smilesCandidatesAndPdbIdsAreRecognised() {
        assertThat(EngineRouter.route("CC(=O)Oc1ccccc1C(=O)O").orElseThrow().engine())
                .isEqualTo(VisualizationEngine.MOLECULE);
        assertThat(EngineRouter.route("1hho").orElseThrow())
                .isEqualTo(new EngineRouter.Routed(VisualizationEngine.PROTEIN, "1HHO"));
        assertThat(engine("show the protein 4HHB and its haem groups")).contains(VisualizationEngine.PROTEIN);
    }

    @Test
    void questionsAboutCircuitBehaviourGoToTheCircuitEngine() {
        assertThat(engine("Explain a voltage divider")).contains(VisualizationEngine.CIRCUIT);
        assertThat(engine("How does an RC circuit charge?")).contains(VisualizationEngine.CIRCUIT);
        assertThat(engine("resistors in parallel")).contains(VisualizationEngine.CIRCUIT);
        assertThat(engine("Kirchhoff's voltage law")).contains(VisualizationEngine.CIRCUIT);
        // How a part works inside, or circuit boards, stay with the storyboard (an animation).
        assertThat(engine("How does a capacitor store charge?")).isEmpty();
        assertThat(engine("How is a PCB made?")).isEmpty();
    }

    @Test
    void ordinaryRequestsAreLeftToTheStoryboard() {
        assertThat(engine("Why is the area of a circle pi r squared?")).isEmpty();
        assertThat(engine("a^2 + b^2 = c^2")).isEmpty();
        assertThat(engine("What happened in 1945 in Europe")).isEmpty(); // a year is not a PDB ID
        assertThat(engine("photosynthesis")).isEmpty();
        // A formula looks like SMILES to the rules; RDKit then rejects it before anything is decided.
        assertThat(engine("E=mc2")).contains(VisualizationEngine.MOLECULE);
    }

    @Test
    void codeAndSingleTokensAreHashedExactly() {
        String a = VisualizationService.hash("c1ccccc1", null, VisualizationEngine.AUTO);
        assertThat(VisualizationService.hash("C1CCCCC1", null, VisualizationEngine.AUTO)).isNotEqualTo(a);
        assertThat(VisualizationService.hash("Why  IS the sky blue", null, VisualizationEngine.AUTO))
                .isEqualTo(VisualizationService.hash("why is the sky blue", null, VisualizationEngine.AUTO));
        assertThat(VisualizationService.hash("aspirin", null, VisualizationEngine.MOLECULE))
                .isNotEqualTo(VisualizationService.hash("aspirin", null, VisualizationEngine.AUTO));
    }
}
