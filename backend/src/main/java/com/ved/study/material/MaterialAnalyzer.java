package com.ved.study.material;

import java.time.Clock;

import com.ved.document.DocumentAnalyzer;
import com.ved.study.ai.StudyAi;
import com.ved.study.ai.StudyAiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pipeline stage 2: asks the AI to chunk the text and design topics, then stores the study material.
 * Idempotent: an existing material for the document is replaced.
 */
@Component
class MaterialAnalyzer implements DocumentAnalyzer {

    private final StudyAi ai;
    private final StudyMaterialRepository materials;
    private final StudyChunkRepository chunks;
    private final StudyTopicRepository topics;
    private final TransactionTemplate tx;
    private final Clock clock;

    MaterialAnalyzer(StudyAi ai, StudyMaterialRepository materials, StudyChunkRepository chunks,
                     StudyTopicRepository topics, TransactionTemplate tx, Clock clock) {
        this.ai = ai;
        this.materials = materials;
        this.chunks = chunks;
        this.topics = topics;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public Result analyze(Input input) throws AnalysisException {
        StudyAi.MaterialDraft draft;
        try {
            draft = ai.buildMaterial(new StudyAi.MaterialSpec(input.filename(), input.pages().stream()
                    .map(p -> new StudyAi.PageText(p.pageFrom(), p.pageTo(), p.text()))
                    .toList()));
        } catch (StudyAiException e) {
            throw new AnalysisException(e.isInputRejected() ? e.getMessage()
                    : "Building the study guide failed: " + e.getMessage(), e);
        }
        tx.executeWithoutResult(status -> {
            materials.deleteByDocumentId(input.documentId());
            materials.flush();
            StudyMaterial material = materials.save(new StudyMaterial(input.documentId(), input.ownerId(),
                    truncate(draft.title(), 300), draft.summary(), draft.language(), draft.kind(), draft.model(),
                    clock.instant()));
            draft.chunks().forEach(c -> chunks.save(new StudyChunk(material.getId(), c.position(),
                    truncate(c.heading(), 300), c.content(), c.pageFrom(), c.pageTo())));
            draft.topics().forEach(t -> topics.save(new StudyTopic(material.getId(), t.position(),
                    truncate(t.title(), 300), t.summary(), t.keyConcepts(), t.difficulty(), t.chunkPositions())));
        });
        return new Result(truncate(draft.title(), 300));
    }

    private static String truncate(String value, int max) {
        String v = value == null ? "" : value.strip();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
