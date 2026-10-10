package com.ved.study.read;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ved.common.error.ApplicationException;
import com.ved.knowledge.figure.FigureViews;
import com.ved.knowledge.retrieval.ContextAssembler;
import com.ved.knowledge.retrieval.ContextAssembler.Context;
import com.ved.knowledge.retrieval.ContextAssembler.Section;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.NodeRow;
import com.ved.study.LearningActivityEvent;
import com.ved.study.ai.StudyAi;
import com.ved.study.material.MaterialAccess;
import com.ved.study.material.StudyMaterial;
import com.ved.study.read.ReadResponses.AskRequest;
import com.ved.study.read.ReadResponses.AskResponse;
import com.ved.study.read.ReadResponses.NotesResponse;
import com.ved.study.read.ReadResponses.SourceRef;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Read mode: long-form study notes per tree node and "ask your document" answers, both grounded in the
 * knowledge store, citing sections and embedding the document's own figures. Notes are generated once.
 *
 * <p>Notes are map-reduce over the tree: a section (or a small chapter) gets full notes from its text; a
 * large chapter gets overview notes from its subsections' summaries (the "map" already done at indexing
 * time) and the reader opens each subsection's own notes from there.
 */
@Service
public class ReadService {

    private final MaterialAccess access;
    private final KnowledgeStore store;
    private final ContextAssembler context;
    private final FigureViews figures;
    private final StudyAi ai;
    private final StudyNoteRepository notes;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    ReadService(MaterialAccess access, KnowledgeStore store, ContextAssembler context, FigureViews figures, StudyAi ai,
                StudyNoteRepository notes, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.access = access;
        this.store = store;
        this.context = context;
        this.figures = figures;
        this.ai = ai;
        this.notes = notes;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    public NotesResponse notes(UUID userId, UUID documentId, UUID nodeId, NoteStyle style) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        NodeRow node = nodes.get(nodeId);
        if (node == null) {
            throw ApplicationException.notFound("Section");
        }
        boolean overview = isLarge(documentId, node, nodes);
        StudyNote note = notes.findByNodeIdAndStyle(nodeId, style)
                .orElseGet(() -> generate(material, node, nodes, overview, style));
        return new NotesResponse(nodeId, style, overview ? "overview" : "section", note.getMarkdown(),
                figures.byIds(note.getFigureIds()), refs(note.getSourceNodeIds(), nodes), note.getCreatedAt());
    }

    public AskResponse ask(UUID userId, UUID documentId, AskRequest request) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        List<UUID> scope = request.nodeId() != null && nodes.containsKey(request.nodeId()) ? List.of(request.nodeId())
                : List.of();
        Context sources = context.forQuestion(documentId, request.question(), scope, access.budgets().ask().chars(),
                access.budgets().ask().figures());
        if (sources.isEmpty()) {
            // Nothing matched: give the model the outline so it can say what the document does cover.
            sources = context.forNodes(documentId, nodes.values().stream().filter(n -> n.level() == 1)
                    .map(NodeRow::id).toList(), 2_000, 0);
        }
        List<StudyAi.ChatTurn> history = request.history() == null ? List.of()
                : request.history().stream().map(t -> new StudyAi.ChatTurn(t.role(), t.text())).toList();
        StudyAi.AnswerDraft draft = ai.answer(new StudyAi.AnswerSpec(material.getTitle(), material.getLanguage(),
                request.question(), history, sources.sections(), sources.figures(), "answer", ""));

        Map<String, UUID> bySection = sources.sections().stream()
                .collect(Collectors.toMap(Section::id, Section::nodeId, (a, b) -> a));
        List<UUID> cited = draft.citedSectionIds() == null ? List.of()
                : draft.citedSectionIds().stream().map(bySection::get).filter(Objects::nonNull).distinct().toList();
        tx.executeWithoutResult(status -> events.publishEvent(LearningActivityEvent.of(userId,
                LearningActivityEvent.Type.TUTOR_QUESTION, clock.instant())
                .document(documentId, material.getTitle()).build()));
        return new AskResponse(draft.answer(), draft.grounded(), refs(cited, nodes), figures.byIds(uuids(draft.figureIds())));
    }

    // ------------------------------------------------------------------------------- notes generation

    private StudyNote generate(StudyMaterial material, NodeRow node, Map<UUID, NodeRow> nodes, boolean overview,
                               NoteStyle style) {
        UUID documentId = material.getDocumentId();
        Context sources;
        if (overview) {
            List<NodeRow> children = nodes.values().stream().filter(n -> node.id().equals(n.parentId())).toList();
            Map<UUID, String> childNotes = notes.findByNodeIdInAndStyle(children.stream().map(NodeRow::id).toList(), style).stream()
                    .collect(Collectors.toMap(StudyNote::getNodeId, StudyNote::getMarkdown));
            Context own = context.forNodes(documentId, List.of(node.id()), 3_000, 0);
            Context kids = context.childSummaries(documentId, node.id(),
                    children.stream().map(c -> childNotes.get(c.id())).toList());
            List<Section> sections = new ArrayList<>();
            own.sections().stream().filter(s -> s.nodeId().equals(node.id()) && !s.passages().isEmpty())
                    .findFirst().ifPresent(sections::add);
            sections.addAll(kids.sections());
            sources = new Context(sections, kids.figures());
        } else {
            sources = context.forNodes(documentId, List.of(node.id()), access.budgets().notes().chars(),
                    access.budgets().notes().figures());
        }
        StudyAi.NotesDraft draft = ai.notes(new StudyAi.NotesSpec(material.getTitle(), material.getLanguage(),
                material.getKind(), node.title(), ContextAssembler.breadcrumb(nodes, node), overview ? "overview" : "section",
                style.name().toLowerCase(Locale.ROOT), sources.sections(), sources.figures()));
        Map<String, UUID> bySection = sources.sections().stream()
                .collect(Collectors.toMap(Section::id, Section::nodeId, (a, b) -> a));
        List<UUID> cited = draft.citedSectionIds() == null ? List.of()
                : draft.citedSectionIds().stream().map(bySection::get).filter(Objects::nonNull).distinct().toList();
        StudyNote note = new StudyNote(node.id(), style, draft.markdown(), uuids(draft.figureIds()), cited, draft.model(),
                clock.instant());
        try {
            return tx.execute(status -> notes.saveAndFlush(note));
        } catch (DataIntegrityViolationException e) {
            return notes.findByNodeIdAndStyle(node.id(), style).orElseThrow(() -> e); // generated concurrently
        }
    }

    /** Large = has subsections and more text than fits one notes call: gets overview notes. */
    private boolean isLarge(UUID documentId, NodeRow node, Map<UUID, NodeRow> nodes) {
        boolean hasChildren = nodes.values().stream().anyMatch(n -> node.id().equals(n.parentId()));
        if (!hasChildren) {
            return false;
        }
        int chars = store.chunksInSubtree(documentId, node.path()).stream().mapToInt(c -> c.content().length()).sum();
        return chars > access.budgets().notes().chars();
    }

    // ------------------------------------------------------------------------------- helpers

    private Map<UUID, NodeRow> nodeMap(UUID documentId) {
        return store.nodes(documentId).stream()
                .collect(Collectors.toMap(NodeRow::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static List<SourceRef> refs(List<UUID> nodeIds, Map<UUID, NodeRow> nodes) {
        return nodeIds.stream().map(nodes::get).filter(Objects::nonNull)
                .map(n -> new SourceRef(ContextAssembler.citationId(n), n.id(), n.label(), n.title(),
                        n.pageFrom() == n.pageTo() ? String.valueOf(n.pageFrom()) : n.pageFrom() + "-" + n.pageTo()))
                .toList();
    }

    private static List<UUID> uuids(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (String id : ids) {
            try {
                out.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // the inference service only returns ids it was given
            }
        }
        return out;
    }
}
