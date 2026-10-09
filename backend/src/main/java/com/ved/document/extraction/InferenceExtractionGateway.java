package com.ved.document.extraction;

import java.nio.charset.StandardCharsets;
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

    record ExtractResponse(String markdown, String engine, int pageCount, int textPages, int ocrPages,
                           List<SegmentDto> segments) {
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
            return new ExtractionResult(r.markdown(), r.engine(), r.pageCount(), r.textPages(), r.ocrPages(),
                    segments);
        } catch (InferenceException e) {
            throw new ExtractionException(e.isClientError()
                    ? "Document could not be processed: " + e.getMessage()
                    : "Text extraction is unavailable right now, please retry later", e);
        }
    }
}
