package com.ved.study.material;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ved.document.DocumentAnalyzer;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.NodeRow;
import com.ved.study.ai.StudyAi;
import com.ved.study.ai.StudyAiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Last pipeline stage: designs the learning path from the indexed document tree (numbering, titles,
 * summaries, key terms). No raw text is sent: the outline is enough to group sections into topics.
 * Idempotent: an existing material for the document is replaced.
 */
@Component
class MaterialAnalyzer implements DocumentAnalyzer {

    static final int SAMPLE_CHARS = 1_500;

    private final StudyAi ai;
    private final KnowledgeStore knowledge;
    private final StudyMaterialRepository materials;
    private final StudyTopicRepository topics;
    private final TransactionTemplate tx;
    private final Clock clock;

    MaterialAnalyzer(StudyAi ai, KnowledgeStore knowledge, StudyMaterialRepository materials,
                     StudyTopicRepository topics, TransactionTemplate tx, Clock clock) {
        this.ai = ai;
        this.knowledge = knowledge;
        this.materials = materials;
        this.topics = topics;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public Result analyze(Input input) throws AnalysisException {
        List<NodeRow> nodes = knowledge.nodes(input.documentId());
        if (nodes.isEmpty()) {
            throw new AnalysisException("No structure was found in this document", null);
        }
        Map<String, NodeRow> byKey = nodes.stream().collect(Collectors.toMap(n -> n.id().toString(), Function.identity()));
        StudyAi.MaterialDraft draft;
        try {
            draft = ai.buildMaterial(new StudyAi.MaterialSpec(input.filename(), sample(input.documentId(), nodes),
                    nodes.stream().map(n -> new StudyAi.OutlineNode(n.id().toString(), n.label(), n.title(), n.level(),
                            n.pageFrom() == n.pageTo() ? String.valueOf(n.pageFrom()) : n.pageFrom() + "-" + n.pageTo(),
                            n.summary() == null ? "" : n.summary(), n.keyTerms())).toList()));
        } catch (StudyAiException e) {
            throw new AnalysisException(e.isInputRejected() ? e.getMessage()
                    : "Building the learning path failed: " + e.getMessage(), e, !e.isInputRejected());
        }
        tx.executeWithoutResult(status -> {
            materials.deleteByDocumentId(input.documentId());
            materials.flush();
            StudyMaterial material = materials.save(new StudyMaterial(input.documentId(), input.ownerId(),
                    truncate(draft.title(), 300), draft.summary(), draft.language(), draft.kind(), draft.model(),
                    clock.instant()));
            draft.topics().forEach(t -> {
                List<UUID> nodeIds = t.nodeKeys().stream().filter(byKey::containsKey).map(UUID::fromString).toList();
                if (!nodeIds.isEmpty()) {
                    topics.save(new StudyTopic(material.getId(), t.position(), truncate(t.title(), 300), t.summary(),
                            t.keyConcepts(), t.difficulty(), nodeIds));
                }
            });
        });
        return new Result(truncate(draft.title(), 300));
    }

    /** A little real text helps the model identify language and kind of material. */
    private String sample(UUID documentId, List<NodeRow> nodes) {
        StringBuilder sb = new StringBuilder();
        for (NodeRow n : nodes) {
            for (KnowledgeStore.ChunkRow c : knowledge.chunksOfNode(n.id())) {
                sb.append(c.content()).append("\n");
                if (sb.length() >= SAMPLE_CHARS) {
                    return sb.substring(0, SAMPLE_CHARS);
                }
            }
        }
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        String v = value == null ? "" : value.strip();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
