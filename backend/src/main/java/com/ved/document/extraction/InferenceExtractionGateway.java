package com.ved.document.extraction;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.ved.common.inference.InferenceClient;
import com.ved.common.inference.InferenceException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** {@code POST /v1/extract} on the inference service (multipart field {@code file}). */
@Component
class InferenceExtractionGateway implements ExtractionGateway {

    private final InferenceClient inference;

    InferenceExtractionGateway(InferenceClient inference) {
        this.inference = inference;
    }

    record SegmentDto(int pageFrom, int pageTo, String method, String markdown) {
    }

    record NodeDto(String key, String parentKey, int level, String path, String label, String title, int ordinal,
                   int pageFrom, int pageTo) {
    }

    record ChunkDto(String key, String nodeKey, int ordinal, String text, int pageFrom, int pageTo) {
    }

    record FigureDto(String key, String nodeKey, String chunkKey, int ordinal, int page, List<Float> bbox,
                     String caption, String mime, int width, int height, String imageBase64) {
    }

    record TreeDto(List<NodeDto> nodes, List<ChunkDto> chunks, List<FigureDto> figures) {
    }

    record ExtractResponse(String markdown, String engine, String language, int pageCount, int textPages, int ocrPages,
                           List<SegmentDto> segments, TreeDto tree) {
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) throws ExtractionException {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(request.filename(), StandardCharsets.UTF_8)
                .build());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(request.content(), partHeaders));
        try {
            ExtractResponse r = inference.postMultipart("/v1/extract", body, ExtractResponse.class);
            List<ExtractionResult.Segment> segments = r.segments() == null ? List.of() : r.segments().stream()
                    .map(s -> new ExtractionResult.Segment(s.pageFrom(), s.pageTo(), s.method(), s.markdown()))
                    .toList();
            return new ExtractionResult(r.markdown(), r.engine(), r.language(), r.pageCount(), r.textPages(),
                    r.ocrPages(), segments, toTree(r.tree()));
        } catch (IllegalArgumentException e) {
            throw new ExtractionException("Text extraction returned malformed data, please retry", e, true);
        } catch (InferenceException e) {
            throw new ExtractionException(e.isInputRejected()
                    ? "Document could not be processed: " + e.getMessage()
                    : e.userMessage("Text extraction is unavailable right now"), e, !e.isInputRejected(),
                    e.retryAfter().orElse(null));
        }
    }

    private static ExtractionResult.Tree toTree(TreeDto tree) {
        if (tree == null) {
            return new ExtractionResult.Tree(List.of(), List.of(), List.of());
        }
        Base64.Decoder base64 = Base64.getDecoder();
        return new ExtractionResult.Tree(
                tree.nodes().stream().map(n -> new ExtractionResult.Node(n.key(), n.parentKey(), n.level(), n.path(),
                        n.label(), n.title(), n.ordinal(), n.pageFrom(), n.pageTo())).toList(),
                tree.chunks().stream().map(c -> new ExtractionResult.Chunk(c.key(), c.nodeKey(), c.ordinal(), c.text(),
                        c.pageFrom(), c.pageTo())).toList(),
                tree.figures().stream().map(f -> new ExtractionResult.Figure(f.key(), f.nodeKey(), f.chunkKey(),
                        f.ordinal(), f.page(), f.bbox(), f.caption(), f.mime(), f.width(), f.height(),
                        base64.decode(f.imageBase64()))).toList());
    }
}
