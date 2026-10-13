package com.sapio.knowledge.figure;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.knowledge.store.KnowledgeStore.FigureRow;
import org.springframework.stereotype.Component;

/** Resolves figure ids to {@link FigureView}s with freshly signed URLs. */
@Component
public class FigureViews {

    private final KnowledgeStore store;
    private final FigureUrlSigner signer;

    FigureViews(KnowledgeStore store, FigureUrlSigner signer) {
        this.store = store;
        this.signer = signer;
    }

    public Map<String, FigureView> byIds(Collection<UUID> ids) {
        return toMap(store.figuresByIds(ids));
    }

    public Map<String, FigureView> toMap(List<FigureRow> rows) {
        Map<String, FigureView> out = new LinkedHashMap<>();
        rows.forEach(f -> out.put(f.id().toString(), view(f)));
        return out;
    }

    public FigureView view(FigureRow f) {
        return new FigureView(f.id(), signer.url(f.id()), f.caption(), f.description(), f.kind(), f.width(),
                f.height(), f.page(), f.nodeId());
    }
}
