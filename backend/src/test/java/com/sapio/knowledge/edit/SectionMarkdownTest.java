package com.sapio.knowledge.edit;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionMarkdownTest {

    private static final UUID FIGURE = UUID.randomUUID();

    @Test
    void keepsCodeMathAndTablesWholeAcrossBlankLines() {
        String md = "Intro.\n\n```java\nint a;\n\nint b;\n```\n\n$$\nx^2\n\n+ y\n$$\n\n| a |\n|---|\n| 1 |";
        assertThat(SectionMarkdown.parse(md, Set.of()).blocks()).containsExactly(
                "Intro.", "```java\nint a;\n\nint b;\n```", "$$\nx^2\n\n+ y\n$$", "| a |\n|---|\n| 1 |");
    }

    @Test
    void figuresLeaveTheTextAndRememberWhereTheyWere() {
        String md = "![Osmosis](figure:" + FIGURE + ")\n\nFirst.\n\nSecond.";
        SectionMarkdown.Parsed parsed = SectionMarkdown.parse(md, Set.of(FIGURE));
        assertThat(parsed.blocks()).containsExactly("First.", "Second.");
        assertThat(parsed.figures()).containsEntry(FIGURE, -1);

        assertThatThrownBy(() -> SectionMarkdown.parse(md, Set.of())).isInstanceOf(ApplicationException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void aTypoFixReplacesOnlyTheChunkItIsIn() {
        String a = "A".repeat(800);
        String b = "B".repeat(800);
        String c = "C".repeat(800);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        List<SectionMarkdown.Existing> existing = List.of(new SectionMarkdown.Existing(first, a),
                new SectionMarkdown.Existing(second, b), new SectionMarkdown.Existing(third, c));

        List<SectionMarkdown.Planned> plan = SectionMarkdown.plan(List.of(a, "B".repeat(799) + "b", c), existing);

        assertThat(plan).extracting(SectionMarkdown.Planned::kept).containsExactly(true, false, true);
        assertThat(plan.get(0).id()).isEqualTo(first);
        assertThat(plan.get(2).id()).isEqualTo(third);
        assertThat(plan.get(1).id()).isNotIn(first, second, third);
    }

    @Test
    void anInsertedParagraphBecomesItsOwnChunkAndNeighboursStay() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<SectionMarkdown.Existing> existing = List.of(new SectionMarkdown.Existing(first, "One.\n\nTwo."),
                new SectionMarkdown.Existing(second, "Three."));

        List<SectionMarkdown.Planned> plan = SectionMarkdown.plan(List.of("One.", "Two.", "New.", "Three."), existing);

        assertThat(plan).extracting(SectionMarkdown.Planned::text).containsExactly("One.\n\nTwo.", "New.", "Three.");
        assertThat(plan).extracting(SectionMarkdown.Planned::kept).containsExactly(true, false, true);
        assertThat(plan.get(1).from()).isEqualTo(2);
    }

    @Test
    void newTextIsPackedLikeExtraction() {
        String para = "x".repeat(500);
        List<SectionMarkdown.Planned> plan = SectionMarkdown.plan(List.of(para, para, para, para), List.of());
        // 500 + 500 reaches the 900 target: a new chunk starts after every two paragraphs.
        assertThat(plan).hasSize(2).allMatch(p -> !p.kept());
        assertThat(plan.get(0).to()).isEqualTo(1);
    }

    @Test
    void measuresHowMuchActuallyChanged() {
        List<String> before = List.of("The cell membrane is selectively permeable.", "Osmosis moves water.");
        assertThat(SectionMarkdown.changedChars(before, before)).isZero();
        // One word fixed: only the changed middle counts, not the whole paragraph.
        assertThat(SectionMarkdown.changedChars(before,
                List.of("The cell membrane is semi-permeable.", "Osmosis moves water."))).isLessThan(15);
        // A new paragraph counts in full.
        assertThat(SectionMarkdown.changedChars(before,
                List.of(before.get(0), "Diffusion moves solutes.", before.get(1)))).isEqualTo(24);
    }
}
