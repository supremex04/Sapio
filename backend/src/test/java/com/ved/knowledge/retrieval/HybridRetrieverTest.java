package com.ved.knowledge.retrieval;

import java.util.List;
import java.util.UUID;

import com.ved.knowledge.store.KnowledgeStore.Ranked;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRetrieverTest {

    @Test
    void reciprocalRankFusionRewardsAgreementAndKeepsHighlights() {
        UUID node = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<Ranked> keyword = List.of(new Ranked(a, node, 9.0, "**osmosis** moves water"), new Ranked(b, node, 1.0, "b"));
        List<Ranked> vector = List.of(new Ranked(c, node, 0.9, "c"), new Ranked(a, node, 0.8, "plain a"));

        List<HybridRetriever.Hit> hits = HybridRetriever.fuse(List.of(keyword, vector), List.of(1.0, 1.0), 10, 60);

        assertThat(hits.getFirst().id()).isEqualTo(a); // in both lists
        assertThat(hits.getFirst().snippet()).contains("**osmosis**");
        assertThat(hits).extracting(HybridRetriever.Hit::id).containsExactly(a, c, b);
        assertThat(HybridRetriever.fuse(List.of(keyword, vector), List.of(1.0, 1.0), 1, 60)).hasSize(1);
    }

    @Test
    void weightsDecideBetweenTheRankersTopResults() {
        UUID node = UUID.randomUUID();
        UUID keywordTop = UUID.randomUUID();
        UUID vectorTop = UUID.randomUUID();
        List<Ranked> keyword = List.of(new Ranked(keywordTop, node, 5.0, "k"));
        List<Ranked> vector = List.of(new Ranked(vectorTop, node, 0.7, "v"));

        assertThat(HybridRetriever.fuse(List.of(keyword, vector), List.of(1.0, 2.0), 2, 10).getFirst().id())
                .isEqualTo(vectorTop);
        assertThat(HybridRetriever.fuse(List.of(keyword, vector), List.of(2.0, 1.0), 2, 10).getFirst().id())
                .isEqualTo(keywordTop);
    }
}
