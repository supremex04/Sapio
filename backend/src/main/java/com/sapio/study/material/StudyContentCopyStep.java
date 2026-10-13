package com.sapio.study.material;

import java.util.List;
import java.util.Map;

import com.sapio.document.copy.DocumentCopy;
import com.sapio.document.copy.DocumentCopyStep;
import org.springframework.stereotype.Component;

import static com.sapio.document.copy.DocumentCopy.mapped;
import static com.sapio.document.copy.DocumentCopy.mappedArray;
import static com.sapio.document.copy.DocumentCopy.set;

/**
 * Copies a document's study content: learning path, generated lessons, question bank and section notes. All of it
 * becomes the new owner's; what they generate later (new lessons, Expand / Revision notes) is written to their copy
 * only. Learner state (progress, attempts, assessments, review cards) is never copied.
 */
@Component
class StudyContentCopyStep implements DocumentCopyStep {

    /** After the knowledge store: topics, questions and notes reference its nodes and figures. */
    static final int ORDER = 200;

    private static final String MATERIALS = "(SELECT id FROM study_materials WHERE document_id = :source)";
    private static final String TOPICS = "(SELECT id FROM study_topics WHERE material_id IN " + MATERIALS + ")";
    private static final String NODES = "(SELECT id FROM document_nodes WHERE document_id = :source)";

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void copy(DocumentCopy copy) {
        copy.mapIds("SELECT id FROM study_materials WHERE document_id = :source");
        copy.copyRows("study_materials", set("id", mapped("src.id"), "document_id", ":target", "owner_id", ":owner"),
                "src.document_id = :source");

        copy.mapIds("SELECT id FROM study_topics WHERE material_id IN " + MATERIALS);
        copy.copyRows("study_topics", set("id", mapped("src.id"), "material_id", mapped("src.material_id"),
                "node_ids", mappedArray("src.node_ids")), "src.material_id IN " + MATERIALS);

        copy.mapIds("SELECT id FROM study_lessons WHERE topic_id IN " + TOPICS);
        copy.copyRows("study_lessons", set("id", mapped("src.id"), "topic_id", mapped("src.topic_id"),
                "figure_ids", mappedArray("src.figure_ids")), "src.topic_id IN " + TOPICS);

        copy.mapIds("SELECT id FROM study_questions WHERE material_id IN " + MATERIALS);
        copy.copyRows("study_questions", set("id", mapped("src.id"), "material_id", mapped("src.material_id"),
                "topic_id", mapped("src.topic_id"), "lesson_id", mapped("src.lesson_id"),
                "source_node_ids", mappedArray("src.source_node_ids")), "src.material_id IN " + MATERIALS);

        copy.mapIds("SELECT id FROM study_notes WHERE node_id IN " + NODES);
        copy.copyRows("study_notes", set("id", mapped("src.id"), "node_id", mapped("src.node_id"),
                "figure_ids", mappedArray("src.figure_ids"), "source_node_ids", mappedArray("src.source_node_ids")),
                "src.node_id IN " + NODES);

        // Lesson and note markdown embed figures as figure:<id>: point them at the copy's figures.
        rewrite(copy, "study_lessons", "sections_json", """
                SELECT l.id, l.sections_json AS text FROM study_lessons l
                JOIN study_topics t ON t.id = l.topic_id JOIN study_materials m ON m.id = t.material_id
                WHERE m.document_id = ?""");
        rewrite(copy, "study_notes", "markdown", """
                SELECT n.id, n.markdown AS text FROM study_notes n JOIN document_nodes d ON d.id = n.node_id
                WHERE d.document_id = ?""");
    }

    private static void rewrite(DocumentCopy copy, String table, String column, String select) {
        List<Map<String, Object>> rows = copy.jdbc().queryForList(select, copy.targetId());
        copy.jdbc().batchUpdate("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", rows.stream()
                .map(r -> new Object[] {copy.rewriteIds((String) r.get("text")), r.get("id")})
                .toList());
    }
}
