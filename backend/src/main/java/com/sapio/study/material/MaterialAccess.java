package com.sapio.study.material;

import java.util.List;
import java.util.UUID;

import com.sapio.study.StudyProperties;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.DocumentService;
import com.sapio.knowledge.retrieval.ContextAssembler;
import com.sapio.study.question.StudyQuestion;
import com.sapio.study.question.StudyQuestionRepository;
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
    private final StudyQuestionRepository questions;
    private final DocumentService documents;
    private final ContextAssembler context;
    private final StudyProperties.Context budgets;

    MaterialAccess(StudyMaterialRepository materials, StudyTopicRepository topics, StudyQuestionRepository questions,
                   DocumentService documents, ContextAssembler context, StudyProperties properties) {
        this.budgets = properties.context();
        this.materials = materials;
        this.topics = topics;
        this.questions = questions;
        this.documents = documents;
        this.context = context;
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

    /** Context budgets per use ({@code sapio.study.context}). */
    public StudyProperties.Context budgets() {
        return budgets;
    }

    /** The topic's sections (its tree nodes' text, in order) and figures, within a budget. */
    public ContextAssembler.Context sources(StudyMaterial material, StudyTopic topic, StudyProperties.Budget budget) {
        return context.forNodes(material.getDocumentId(), topic.getNodeIds(), budget.chars(), budget.figures());
    }

    /** Sources cited by a question, falling back to its topic's nodes. */
    public ContextAssembler.Context sources(StudyMaterial material, StudyQuestion question, StudyTopic topic) {
        List<UUID> nodes = question.getSourceNodeIds().isEmpty() ? topic.getNodeIds() : question.getSourceNodeIds();
        return context.forNodes(material.getDocumentId(), nodes, budgets.grade().chars(), budgets.grade().figures());
    }

    private static StudyMaterial requireOwned(UUID userId, StudyMaterial material) {
        if (!material.getOwnerId().equals(userId)) {
            throw ApplicationException.notFound("Study material");
        }
        return material;
    }
}
