package com.sapio.knowledge.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sapio.knowledge.KnowledgeProperties;
import com.sapio.knowledge.store.KnowledgeStore.NodeRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyUnitsTest {

    private final KnowledgeService service = new KnowledgeService(null, null, null, null,
            new KnowledgeProperties("s", Duration.ofHours(1), null, null, new KnowledgeProperties.Reading(500, 2_000)));

    @Test
    void eachPassageGetsOneUnitAtTheHighestLevelThatFits() {
        // 1 (small chapter: 1.1, 1.2) | 2 (too big: 2.1 fits, 2.2 is a fragment)
        NodeRow c1 = node("1", null, 1, 5);
        NodeRow s11 = node("1.1", c1, 1, 2);
        NodeRow s12 = node("1.2", c1, 3, 5);
        NodeRow c2 = node("2", null, 6, 20);
        NodeRow s21 = node("2.1", c2, 6, 15);
        NodeRow s22 = node("2.2", c2, 16, 20);
        Map<UUID, Integer> chars = Map.of(c1.id(), 50, s11.id(), 300, s12.id(), 400,
                c2.id(), 100, s21.id(), 1_800, s22.id(), 200);

        Map<UUID, KnowledgeResponses.Unit> units = service.studyUnits(List.of(c1, s11, s12, c2, s21, s22), chars);

        assertThat(units.keySet()).containsExactlyInAnyOrder(c1.id(), s21.id());
        assertThat(units.get(c1.id())).isEqualTo(new KnowledgeResponses.Unit("1", "1.2", 3, 1, 5));
        assertThat(units.get(s21.id())).isEqualTo(new KnowledgeResponses.Unit("2.1", "2.1", 1, 6, 15));
    }

    private static NodeRow node(String label, NodeRow parent, int from, int to) {
        return new NodeRow(UUID.randomUUID(), null, parent == null ? null : parent.id(), label, label, label,
                label.split("\\.").length, 0, from, to, null, List.of(), 0);
    }
}
