package com.sapio.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.knowledge.store.KnowledgeStore.ChunkRow;
import com.sapio.knowledge.store.KnowledgeStore.FigureRow;
import com.sapio.knowledge.store.KnowledgeStore.NodeRow;
import org.springframework.stereotype.Component;

/**
 * Builds compact, structured LLM context from the knowledge store: sections (citation id, breadcrumb, pages,
 * summary, passages) and the figures that belong to them. Never sends the whole document.
 * Format and rules: docs/specs/knowledge.md ("Context injection format").
 */
@Component
public class ContextAssembler {

    private static final Pattern VISUAL = Pattern.compile(
            "\\b(diagram|figure|image|picture|chart|graph|illustrat\\w*|draw\\w*|show me|look like|map|table)\\b",
            Pattern.CASE_INSENSITIVE);

    private final KnowledgeStore store;
    private final HybridRetriever retriever;

    ContextAssembler(KnowledgeStore store, HybridRetriever retriever) {
        this.store = store;
        this.retriever = retriever;
    }

    /** A section of context; {@code id} ("§2.3") is what the model cites. */
    public record Section(String id, UUID nodeId, String label, String title, String path, String pages,
                          String summary, List<String> passages) {
    }

    /** A figure the model may embed as ![caption](figure:id). */
    public record Figure(String id, UUID figureId, String sectionId, String caption, String description) {
    }

    public record Context(List<Section> sections, List<Figure> figures) {
        public boolean isEmpty() {
            return sections.isEmpty();
        }
    }

    public static String citationId(NodeRow node) {
        return "§" + node.path();
    }

    /**
     * Question-driven: hybrid search → small-to-big (neighbouring chunks + node summary) → linked figures
     * (+ semantic figure search when the question is visual).
     */
    public Context forQuestion(UUID documentId, String question, Collection<UUID> scopeNodeIds, int budgetChars,
                               int maxFigures) {
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        List<String> scope = scopeNodeIds.stream().map(nodes::get).filter(n -> n != null).map(NodeRow::path).toList();
        boolean visual = VISUAL.matcher(question).find();
        HybridRetriever.Results results = retriever.search(documentId, question, scope, 10, visual ? 3 : 0);

        Map<UUID, Set<ChunkRow>> byNode = new LinkedHashMap<>();
        int used = 0;
        for (HybridRetriever.Hit hit : results.chunks()) {
            for (ChunkRow c : store.neighbours(hit.nodeId(), ordinalOf(hit.id()), 1)) {
                if (used >= budgetChars) {
                    break;
                }
                if (byNode.computeIfAbsent(c.nodeId(), k -> new LinkedHashSet<>()).add(c)) {
                    used += c.content().length();
                }
            }
        }
        List<Section> sections = toSections(byNode, nodes);
        Set<UUID> chunkIds = byNode.values().stream().flatMap(Set::stream).map(ChunkRow::id).collect(Collectors.toSet());

        LinkedHashMap<UUID, FigureRow> figures = new LinkedHashMap<>();
        store.figuresOfChunks(chunkIds).forEach(f -> figures.putIfAbsent(f.id(), f));
        store.figuresByIds(results.figures().stream().map(HybridRetriever.Hit::id).toList())
                .forEach(f -> figures.putIfAbsent(f.id(), f));
        return new Context(sections, toFigures(figures.values(), nodes, maxFigures));
    }

    /** Topic/node-driven (lessons, questions, notes): the subtrees' text in order, within a budget. */
    public Context forNodes(UUID documentId, Collection<UUID> nodeIds, int budgetChars, int maxFigures) {
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        Map<UUID, Set<ChunkRow>> byNode = new LinkedHashMap<>();
        List<FigureRow> figures = new ArrayList<>();
        int used = 0;
        for (UUID id : nodeIds) {
            NodeRow root = nodes.get(id);
            if (root == null) {
                continue;
            }
            for (ChunkRow c : store.chunksInSubtree(documentId, root.path())) {
                if (used + c.content().length() > budgetChars && used > 0) {
                    break;
                }
                if (byNode.computeIfAbsent(c.nodeId(), k -> new LinkedHashSet<>()).add(c)) {
                    used += c.content().length();
                }
            }
            figures.addAll(store.figuresInSubtree(documentId, root.path()));
            // Sections without own text (e.g. a chapter heading) still contribute their summary.
            byNode.putIfAbsent(id, new LinkedHashSet<>());
        }
        return new Context(toSections(byNode, nodes), toFigures(figures, nodes, maxFigures));
    }

    /** Overview-style context: a node's children as sections made of their summaries (map-reduce "reduce"). */
    public Context childSummaries(UUID documentId, UUID nodeId, Collection<String> childNotes) {
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        List<Section> sections = new ArrayList<>();
        NodeRow parent = nodes.get(nodeId);
        List<String> notes = new ArrayList<>(childNotes);
        int i = 0;
        for (NodeRow child : nodes.values().stream().filter(n -> nodeId.equals(n.parentId()))
                .sorted(Comparator.comparingInt(NodeRow::ordinal)).toList()) {
            String note = i < notes.size() ? notes.get(i) : null;
            i++;
            sections.add(new Section(citationId(child), child.id(), child.label(), child.title(), breadcrumb(nodes, child),
                    pages(child), child.summary() == null ? "" : child.summary(),
                    note == null ? List.of() : List.of(truncate(note, 2_500))));
        }
        if (parent != null && sections.isEmpty()) {
            sections.add(new Section(citationId(parent), parent.id(), parent.label(), parent.title(),
                    breadcrumb(nodes, parent), pages(parent), parent.summary() == null ? "" : parent.summary(), List.of()));
        }
        List<FigureRow> figures = parent == null ? List.of() : store.figuresInSubtree(documentId, parent.path());
        return new Context(sections, toFigures(figures, nodes, 4));
    }

    // ------------------------------------------------------------------------------- helpers

    private List<Section> toSections(Map<UUID, Set<ChunkRow>> byNode, Map<UUID, NodeRow> nodes) {
        return byNode.entrySet().stream()
                .filter(e -> nodes.containsKey(e.getKey()))
                .sorted(Comparator.comparingInt(e -> nodes.get(e.getKey()).ordinal()))
                .map(e -> {
                    NodeRow n = nodes.get(e.getKey());
                    List<String> passages = e.getValue().stream().sorted(Comparator.comparingInt(ChunkRow::ordinal))
                            .map(ChunkRow::content).toList();
                    return new Section(citationId(n), n.id(), n.label(), n.title(), breadcrumb(nodes, n), pages(n),
                            n.summary() == null ? "" : n.summary(), passages);
                })
                .toList();
    }

    private static List<Figure> toFigures(Collection<FigureRow> rows, Map<UUID, NodeRow> nodes, int max) {
        return rows.stream()
                .filter(f -> f.description() != null) // only indexed, educational figures
                .distinct()
                .limit(max)
                .map(f -> new Figure(f.id().toString(), f.id(),
                        nodes.containsKey(f.nodeId()) ? citationId(nodes.get(f.nodeId())) : "",
                        f.caption(), f.description()))
                .toList();
    }

    private int ordinalOf(UUID chunkId) {
        return store.chunksByIds(List.of(chunkId)).stream().findFirst().map(ChunkRow::ordinal).orElse(0);
    }

    private Map<UUID, NodeRow> nodeMap(UUID documentId) {
        return store.nodes(documentId).stream().collect(Collectors.toMap(NodeRow::id, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }

    public static String breadcrumb(Map<UUID, NodeRow> nodes, NodeRow node) {
        List<String> parts = new ArrayList<>();
        for (NodeRow n = node; n != null; n = n.parentId() == null ? null : nodes.get(n.parentId())) {
            parts.addFirst(n.label().equals(n.path()) ? n.title() : n.label() + " " + n.title());
        }
        return String.join(" › ", parts);
    }

    private static String pages(NodeRow n) {
        return n.pageFrom() == n.pageTo() ? String.valueOf(n.pageFrom()) : n.pageFrom() + "-" + n.pageTo();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
