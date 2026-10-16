package com.sapio.visualization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SceneChecksTest {

    static StructureIndex structure;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = SceneChecksTest.class.getResourceAsStream("/visualization/haemoglobin-mini.cif")) {
            structure = StructureIndex.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theStructureIsReadFromMmcif() {
        assertThat(structure.residue("A", 92)).contains("ARG");
        assertThat(structure.residue("B", 92)).contains("HIS");
        assertThat(structure.hasLigand("HEM")).isTrue();
        assertThat(structure.hasLigand("HOH")).isFalse(); // water is not a ligand to point at
        assertThat(structure.chains()).extracting(StructureIndex.Chain::description)
                .containsExactly("Hemoglobin subunit alpha", "Hemoglobin subunit beta");
    }

    @Test
    void aResidueThatIsNotWhatTheNarrationSaysIsCorrectedToTheChainWhereItIs() {
        // "the proximal histidine" at A:92: alpha 92 is arginine; beta 92 is the histidine meant.
        SceneChecks.ProteinFocus focus = SceneChecks.protein(structure, "A:92",
                "It pulls the iron, dragging the proximal histidine with it.");
        assertThat(focus.focus()).isEqualTo("B:92");
        assertThat(focus.label()).isEqualTo("His92, chain B (hemoglobin subunit beta)");
        assertThat(SceneChecks.protein(structure, "A:HIS92", "").focus()).isEqualTo("B:92");
        assertThat(SceneChecks.protein(structure, "A:87:HIS", "the histidine").focus()).isEqualTo("A:87");
    }

    @Test
    void pointersThatCannotBeConfirmedAreDroppedNotShownWrong() {
        assertThat(SceneChecks.protein(structure, "A:92", "a tyrosine holds it").focus()).isEmpty();
        assertThat(SceneChecks.protein(structure, "Z:10", "").focus()).isEmpty();
        assertThat(SceneChecks.protein(structure, "ligand:NAG", "").focus()).isEmpty();
        assertThat(SceneChecks.protein(structure, "the active site", "").focus()).isEmpty();
        assertThat(SceneChecks.protein(structure, "A:300-310", "").focus()).isEmpty();
        // Unchecked by name, but there: kept as is.
        assertThat(SceneChecks.protein(structure, "A:92", "this residue").label()).startsWith("Arg92, chain A");
        assertThat(SceneChecks.protein(structure, "ligand:hem", "").focus()).isEqualTo("ligand:HEM");
        assertThat(SceneChecks.protein(structure, "B", "").label()).isEqualTo("chain B (hemoglobin subunit beta)");
    }

    @Test
    void codeIsPointedAtByTextInTheProgramActuallyTraced() {
        String program = """
                def bubble_sort(arr):
                    n = len(arr)
                    for i in range(n):
                        for j in range(0, n - i - 1):
                            if arr[j] > arr[j + 1]:
                                arr[j], arr[j + 1] = arr[j + 1], arr[j]
                    return arr

                print(bubble_sort([5, 1, 4, 2]))
                """;
        assertThat(SceneChecks.lines(program, "if arr[j] > arr[j + 1]:")).isEqualTo("5-5");
        assertThat(SceneChecks.lines(program, "for i in range(n): | arr[j], arr[j + 1] = arr[j + 1], arr[j]"))
                .isEqualTo("3-6");
        assertThat(SceneChecks.lines(program, "7-8")).isEqualTo("7-7"); // the blank line is not code
        assertThat(SceneChecks.lines(program, "8")).isEmpty();
        assertThat(SceneChecks.lines(program, "40-50")).isEmpty(); // past the end: a wrong pointer
        assertThat(SceneChecks.lines(program, "8-12")).isEqualTo("9-9");
        assertThat(SceneChecks.lines(program, "while True:")).isEmpty();
    }
}
