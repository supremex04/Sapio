package com.ved.study.material;

import java.util.List;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.document.DocumentService;
import com.ved.study.ai.StudyAi;
import com.ved.study.question.StudyQuestion;
import com.ved.study.question.StudyQuestionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that answers "may this user study this?" and loads grounded sources. Today access means
 * ownership; class sharing will extend only this class. Unauthorised access looks like 404.
 */
@Component
public class MaterialAccess {

    private final StudyMaterialRepository materials;
    private final StudyTopicRepository topics;
    private final StudyChunkRepository chunks;
    private final StudyQuestionRepository questions;
    private final DocumentService documents;

    MaterialAccess(StudyMaterialRepository materials, StudyTopicRepository topics, StudyChunkRepository chunks,
                   StudyQuestionRepository questions, DocumentService documents) {
        this.materials = materials;
        this.topics = topics;
        this.chunks = chunks;
        this.questions = questions;
        this.documents = documents;
    }

    public record TopicContext(StudyMaterial material, StudyTopic topic) {
    }

    public record QuestionContext(StudyMaterial material, StudyTopic topic, StudyQuestion question) {
    }

    @Transactional(readOnly = true)
    public StudyMaterial requireByDocument(UUID userId, UUID documentId) {
        StudyMaterial material = materials.findByDocumentId(documentId).orElse(null);
        if (material == null) {
            documents.get(userId, documentId); // 404 if not the user's document
            throw new ApplicationException(ErrorCode.MATERIAL_NOT_READY, "The study guide is not ready yet");
        }
        return requireOwned(userId, material);
    }

    @Transactional(readOnly = true)
    public StudyMaterial requireMaterial(UUID userId, UUID materialId) {
        return requireOwned(userId, materials.findById(materialId)
                .orElseThrow(() -> ApplicationException.notFound("Study material")));
    }

    @Transactional(readOnly = true)
    public TopicContext requireTopic(UUID userId, UUID topicId) {
        StudyTopic topic = topics.findById(topicId).orElseThrow(() -> ApplicationException.notFound("Topic"));
        return new TopicContext(requireMaterial(userId, topic.getMaterialId()), topic);
    }

    @Transactional(readOnly = true)
    public QuestionContext requireQuestion(UUID userId, UUID questionId) {
        StudyQuestion question = questions.findById(questionId)
                .orElseThrow(() -> ApplicationException.notFound("Question"));
        StudyTopic topic = topics.findById(question.getTopicId())
                .orElseThrow(() -> ApplicationException.notFound("Question"));
        return new QuestionContext(requireMaterial(userId, question.getMaterialId()), topic, question);
    }

    /** Source chunks a topic is grounded in. */
    @Transactional(readOnly = true)
    public List<StudyAi.SourceChunk> sources(StudyTopic topic) {
        return load(topic.getMaterialId(), topic.getChunkPositions());
    }

    /** Sources cited by a question, falling back to its topic's sources. */
    @Transactional(readOnly = true)
    public List<StudyAi.SourceChunk> sources(StudyQuestion question, StudyTopic topic) {
        List<Integer> positions = question.getSourceChunkPositions().isEmpty()
                ? topic.getChunkPositions() : question.getSourceChunkPositions();
        return load(question.getMaterialId(), positions);
    }

    private List<StudyAi.SourceChunk> load(UUID materialId, List<Integer> positions) {
        return chunks.findByMaterialIdAndPositionInOrderByPosition(materialId, positions).stream()
                .map(c -> new StudyAi.SourceChunk(c.getPosition(), c.getHeading(), c.getContent()))
                .toList();
    }

    private static StudyMaterial requireOwned(UUID userId, StudyMaterial material) {
        if (!material.getOwnerId().equals(userId)) {
            throw ApplicationException.notFound("Study material");
        }
        return material;
    }
}
