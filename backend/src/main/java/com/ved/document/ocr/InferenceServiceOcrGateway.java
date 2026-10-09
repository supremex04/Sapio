package com.ved.document.ocr;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the Python inference service: {@code POST /v1/ocr} (multipart, field {@code file}).
 * See docs/specs/inference-api.md for the contract.
 */
@Component
class InferenceServiceOcrGateway implements OcrGateway {

    private static final Logger log = LoggerFactory.getLogger(InferenceServiceOcrGateway.class);
    static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient client;

    InferenceServiceOcrGateway(RestClient.Builder builder, InferenceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .build();
    }

    /** Wire format of the inference service response. */
    record InferenceOcrResponse(String markdown, String engine, Integer pageCount) {
    }

    /** Wire format of inference service errors (FastAPI {@code detail}). */
    record InferenceError(String detail) {
    }

    @Override
    public OcrResult extract(OcrRequest request) throws OcrException {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(request.filename(), StandardCharsets.UTF_8)
                .build());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(request.content(), partHeaders));
        try {
            InferenceOcrResponse response = client.post()
                    .uri("/v1/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(InferenceOcrResponse.class);
            if (response == null || response.markdown() == null) {
                throw new OcrException("OCR service returned an empty response");
            }
            return new OcrResult(response.markdown(), response.engine(), response.pageCount());
        } catch (HttpClientErrorException e) {
            // 4xx: the document itself could not be processed (corrupt, unsupported, too many pages...)
            InferenceError error = e.getResponseBodyAs(InferenceError.class);
            String detail = error != null && error.detail() != null ? error.detail() : e.getStatusText();
            throw new OcrException("Document could not be processed: " + detail, e);
        } catch (RestClientException e) {
            log.warn("OCR inference call failed", e);
            throw new OcrException("OCR service is unavailable, please retry later", e);
        }
    }
}
