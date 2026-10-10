package com.ved.knowledge.ai;

import java.util.Base64;
import java.util.List;

import com.ved.common.inference.InferenceClient;
import com.ved.common.inference.InferenceException;
import org.springframework.stereotype.Component;

/** Calls {@code /v1/index/*} on the inference service. */
@Component
class InferenceKnowledgeAi implements KnowledgeAi {

    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private final InferenceClient inference;

    InferenceKnowledgeAi(InferenceClient inference) {
        this.inference = inference;
    }

    record FigureDto(String key, String imageBase64, String mime, String caption, String context) {
    }

    record DescribeRequest(String language, List<FigureDto> figures) {
    }

    record DescribeResponse(List<FigureDescription> figures) {
    }

    record SummarizeRequest(String language, List<SummaryInput> nodes) {
    }

    record SummarizeResponse(List<NodeSummary> summaries) {
    }

    record EmbedItemDto(String text, String imageBase64, String mime) {
    }

    record EmbedRequest(String task, List<EmbedItemDto> items) {
    }

    record EmbedResponse(List<float[]> vectors) {
    }

    @Override
    public List<FigureDescription> describeFigures(String language, List<FigureInput> figures) {
        List<FigureDto> dtos = figures.stream()
                .map(f -> new FigureDto(f.key(), BASE64.encodeToString(f.image()), f.mime(), f.caption(), f.context()))
                .toList();
        return post("/v1/index/figures", new DescribeRequest(language, dtos), DescribeResponse.class).figures();
    }

    @Override
    public List<NodeSummary> summarize(String language, List<SummaryInput> nodes) {
        return post("/v1/index/summaries", new SummarizeRequest(language, nodes), SummarizeResponse.class).summaries();
    }

    @Override
    public List<float[]> embed(EmbedTask task, List<EmbedInput> items) {
        List<EmbedItemDto> dtos = items.stream()
                .map(i -> new EmbedItemDto(i.text(), i.image() == null ? null : BASE64.encodeToString(i.image()), i.mime()))
                .toList();
        List<float[]> vectors = post("/v1/index/embed", new EmbedRequest(task.name(), dtos), EmbedResponse.class).vectors();
        if (vectors == null || vectors.size() != items.size()) {
            throw new KnowledgeAiException("The embedding service returned an incomplete result");
        }
        return vectors;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return inference.postJson(path, body, type);
        } catch (InferenceException e) {
            throw new KnowledgeAiException(e.getMessage() == null || e.getMessage().isBlank()
                    ? "Indexing is unavailable right now, please retry in a moment" : e.getMessage());
        }
    }
}
