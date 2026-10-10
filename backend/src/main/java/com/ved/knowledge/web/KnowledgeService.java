package com.ved.knowledge.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ved.knowledge.KnowledgeProperties;
import com.ved.knowledge.figure.FigureViews;
import com.ved.knowledge.retrieval.ContextAssembler;
import com.ved.knowledge.retrieval.HybridRetriever;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.ChunkRow;
import com.ved.knowledge.store.KnowledgeStore.FigureRow;
import com.ved.knowledge.store.KnowledgeStore.NodeRow;
import com.ved.knowledge.web.KnowledgeResponses.Block;
import com.ved.knowledge.web.KnowledgeResponses.Crumb;
import org.springframework.stereotype.Service;

/** Read side of the knowledge store: navigation, reading view, search, term index. */
@Service
public class KnowledgeService {

    private final KnowledgeAccess access;
    private final KnowledgeStore store;
    private final HybridRetriever retriever;
    private final FigureViews figures;

    private final KnowledgeProperties.Reading reading;

    KnowledgeService(KnowledgeAccess access, KnowledgeStore store, HybridRetriever retriever, FigureViews figures,
                     KnowledgeProperties properties) {
        this.reading = properties.reading();
        this.access = access;
        this.store = store;
        this.retriever = retriever;
        this.figures = figures;
    }

    public KnowledgeResponses.TreeResponse tree(UUID userId, UUID documentId) {
        access.requireDocument(userId, documentId);
        Map<UUID, Integer> counts = store.figureCounts(documentId);
        return new KnowledgeResponses.TreeResponse(documentId, store.nodes(documentId).stream()
                .map(n -> new KnowledgeResponses.NodeSummary(n.id(), n.parentId(), n.path(), n.label(), n.title(),
                        n.level(), n.pageFrom(), n.pageTo(), counts.getOrDefault(n.id(), 0)))
                .toList());
    }

    /** The node's own content in reading order, figures placed after the chunk they belong to. */
    /** The whole document in reading order: every node with its text and (kept) figures in place. */
    public KnowledgeResponses.ContentResponse content(UUID userId, UUID documentId) {
        access.requireDocument(userId, documentId);
        Map<UUID, List<ChunkRow>> chunksByNode = store.chunksOfDocument(documentId).stream()
                .collect(Collectors.groupingBy(ChunkRow::nodeId, LinkedHashMap::new, Collectors.toList()));
        List<FigureRow> shown = store.figuresOfDocument(documentId).stream().filter(f -> f.description() != null)
                .toList();
        Map<UUID, List<FigureRow>> byChunk = shown.stream().filter(f -> f.chunkId() != null)
                .collect(Collectors.groupingBy(FigureRow::chunkId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<FigureRow>> unanchored = shown.stream().filter(f -> f.chunkId() == null)
                .collect(Collectors.groupingBy(FigureRow::nodeId, LinkedHashMap::new, Collectors.toList()));

        List<NodeRow> nodes = store.nodes(documentId);
        Map<UUID, Integer> ownChars = new HashMap<>();
        chunksByNode.forEach((node, chunks) -> ownChars.put(node, chunks.stream().mapToInt(c -> c.content().length()).sum()));
        Map<UUID, KnowledgeResponses.Unit> units = studyUnits(nodes, ownChars);

        List<KnowledgeResponses.ContentSection> sections = new ArrayList<>();
        for (NodeRow n : nodes) {
            List<Block> blocks = new ArrayList<>();
            for (ChunkRow c : chunksByNode.getOrDefault(n.id(), List.of())) {
                blocks.add(new Block("text", c.content(), null, c.pageFrom()));
                byChunk.getOrDefault(c.id(), List.of())
                        .forEach(f -> blocks.add(new Block("figure", null, f.id(), f.page())));
            }
            unanchored.getOrDefault(n.id(), List.of())
                    .forEach(f -> blocks.add(new Block("figure", null, f.id(), f.page())));
            sections.add(new KnowledgeResponses.ContentSection(n.id(), n.parentId(), n.path(), n.label(), n.title(),
                    n.level(), n.pageFrom(), n.pageTo(), blocks, units.get(n.id())));
        }
        return new KnowledgeResponses.ContentResponse(documentId, sections, figures.toMap(shown));
    }

    /**
     * Picks the study units top-down: the first section whose subtree text fits [min, max] is a unit and covers its
     * subsections; a bigger section is split into its children; smaller fragments are covered by no unit of their
     * own. So every passage gets at most one Expand / Revision, and each rewrite sees all of the text it covers.
     */
    Map<UUID, KnowledgeResponses.Unit> studyUnits(List<NodeRow> nodes, Map<UUID, Integer> ownChars) {
        Map<UUID, List<NodeRow>> children = new HashMap<>();
        List<NodeRow> roots = new ArrayList<>();
        for (NodeRow n : nodes) {
            if (n.parentId() == null) {
                roots.add(n);
            } else {
                children.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
            }
        }
        Map<UUID, Integer> subtreeChars = new HashMap<>();
        for (int i = nodes.size() - 1; i >= 0; i--) { // document order: children come after their parent
            NodeRow n = nodes.get(i);
            subtreeChars.put(n.id(), ownChars.getOrDefault(n.id(), 0) + children.getOrDefault(n.id(), List.of())
                    .stream().mapToInt(c -> subtreeChars.getOrDefault(c.id(), 0)).sum());
        }
        Map<UUID, KnowledgeResponses.Unit> units = new HashMap<>();
        ArrayDeque<NodeRow> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            NodeRow n = todo.pollFirst();
            int chars = subtreeChars.getOrDefault(n.id(), 0);
            List<NodeRow> kids = children.getOrDefault(n.id(), List.of());
            if (chars < reading.unitMinChars()) {
                continue; // too little to rewrite; nothing below can qualify either
            }
            if (chars <= reading.unitMaxChars() || kids.isEmpty()) {
                List<NodeRow> covered = new ArrayList<>();
                collect(n, children, covered);
                NodeRow last = covered.getLast();
                units.put(n.id(), new KnowledgeResponses.Unit(n.label(), last.label(), covered.size(),
                        covered.stream().mapToInt(NodeRow::pageFrom).min().orElse(n.pageFrom()),
                        covered.stream().mapToInt(NodeRow::pageTo).max().orElse(n.pageTo())));
            } else {
                todo.addAll(kids);
            }
        }
        return units;
    }

    private static void collect(NodeRow n, Map<UUID, List<NodeRow>> children, List<NodeRow> out) {
        out.add(n);
        children.getOrDefault(n.id(), List.of()).forEach(c -> collect(c, children, out));
    }

    public KnowledgeResponses.NodeResponse node(UUID userId, UUID documentId, UUID nodeId) {
        NodeRow node = access.requireNode(userId, documentId, nodeId);
        List<NodeRow> all = store.nodes(documentId);
        Map<UUID, NodeRow> byId = all.stream().collect(Collectors.toMap(NodeRow::id, Function.identity()));

        List<FigureRow> nodeFigures = store.figuresInSubtree(documentId, node.path()).stream()
                .filter(f -> f.nodeId().equals(nodeId) && f.description() != null).toList();
        Map<UUID, List<FigureRow>> byChunk = nodeFigures.stream().filter(f -> f.chunkId() != null)
                .collect(Collectors.groupingBy(FigureRow::chunkId, LinkedHashMap::new, Collectors.toList()));
        List<Block> blocks = new ArrayList<>();
        for (ChunkRow c : store.chunksOfNode(nodeId)) {
            blocks.add(new Block("text", c.content(), null, c.pageFrom()));
            byChunk.getOrDefault(c.id(), List.of()).forEach(f -> blocks.add(new Block("figure", null, f.id(), f.page())));
        }
        nodeFigures.stream().filter(f -> f.chunkId() == null)
                .forEach(f -> blocks.add(new Block("figure", null, f.id(), f.page())));

        int index = all.indexOf(node);
        List<Crumb> breadcrumb = new ArrayList<>();
        for (NodeRow n = node; n != null; n = n.parentId() == null ? null : byId.get(n.parentId())) {
            breadcrumb.addFirst(crumb(n));
        }
        List<Crumb> children = all.stream().filter(n -> nodeId.equals(n.parentId())).map(KnowledgeService::crumb).toList();
        return new KnowledgeResponses.NodeResponse(
                new KnowledgeResponses.NodeDetail(node.id(), node.path(), node.label(), node.title(), node.level(),
                        node.pageFrom(), node.pageTo(), node.summary(), node.keyTerms()),
                breadcrumb, children,
                index > 0 ? crumb(all.get(index - 1)) : null,
                index < all.size() - 1 ? crumb(all.get(index + 1)) : null,
                blocks, figures.toMap(nodeFigures));
    }

    public KnowledgeResponses.SearchResponse search(UUID userId, UUID documentId, String query, UUID scopeNodeId,
                                                    int limit) {
        access.requireDocument(userId, documentId);
        Map<UUID, NodeRow> nodes = store.nodes(documentId).stream()
                .collect(Collectors.toMap(NodeRow::id, Function.identity()));
        List<String> scope = scopeNodeId == null || !nodes.containsKey(scopeNodeId) ? List.of()
                : List.of(nodes.get(scopeNodeId).path());
        HybridRetriever.Results results = retriever.search(documentId, query.strip(), scope, Math.clamp(limit, 1, 30), 4);
        List<KnowledgeResponses.SearchHit> hits = results.chunks().stream()
                .filter(h -> nodes.containsKey(h.nodeId()))
                .map(h -> {
                    NodeRow n = nodes.get(h.nodeId());
                    return new KnowledgeResponses.SearchHit(h.id(), n.id(), n.label(), n.title(),
                            ContextAssembler.breadcrumb(nodes, n), h.snippet(), Math.round(h.score() * 10_000) / 10_000.0);
                })
                .toList();
        List<FigureRow> figureRows = store.figuresByIds(results.figures().stream().map(HybridRetriever.Hit::id).toList())
                .stream().filter(f -> f.description() != null).toList();
        return new KnowledgeResponses.SearchResponse(query, hits, figureRows.stream().map(figures::view).toList());
    }

    public KnowledgeResponses.TermsResponse terms(UUID userId, UUID documentId) {
        access.requireDocument(userId, documentId);
        Map<UUID, NodeRow> nodes = store.nodes(documentId).stream()
                .collect(Collectors.toMap(NodeRow::id, Function.identity()));
        Map<String, List<Crumb>> grouped = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (KnowledgeStore.TermRow t : store.terms(documentId)) {
            String key = t.term().toLowerCase();
            display.putIfAbsent(key, t.term());
            NodeRow n = nodes.get(t.nodeId());
            if (n != null) {
                List<Crumb> list = grouped.computeIfAbsent(key, k -> new ArrayList<>());
                if (list.stream().noneMatch(c -> c.id().equals(n.id()))) {
                    list.add(crumb(n));
                }
            }
        }
        return new KnowledgeResponses.TermsResponse(grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> new KnowledgeResponses.TermEntry(display.get(e.getKey()), e.getValue()))
                .toList());
    }

    private static Crumb crumb(NodeRow n) {
        return new Crumb(n.id(), n.label(), n.title());
    }
}
