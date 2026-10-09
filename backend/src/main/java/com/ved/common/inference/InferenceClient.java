package com.ved.common.inference;

import java.net.http.HttpClient;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Shared HTTP client for the internal Python inference service (auth header, timeouts, error decoding).
 * Feature adapters (extraction, study AI) build on it. Contract: docs/specs/inference-api.md.
 */
@Component
public class InferenceClient {

    private static final Logger log = LoggerFactory.getLogger(InferenceClient.class);
    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient client;

    public InferenceClient(RestClient.Builder builder, InferenceProperties properties) {
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

    /** Wire format of inference errors (FastAPI {@code detail}). */
    record ErrorBody(Object detail) {
    }

    public <T> T postJson(String path, Object body, Class<T> responseType) throws InferenceException {
        return call(path, () -> client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(responseType));
    }

    public <T> T postMultipart(String path, MultiValueMap<String, ?> body, Class<T> responseType)
            throws InferenceException {
        return call(path, () -> client.post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                .retrieve().body(responseType));
    }

    private <T> T call(String path, Supplier<T> request) throws InferenceException {
        try {
            T response = request.get();
            if (response == null) {
                throw new InferenceException("Empty response from inference service", false, null);
            }
            return response;
        } catch (HttpClientErrorException e) {
            ErrorBody error = e.getResponseBodyAs(ErrorBody.class);
            String detail = error != null && error.detail() instanceof String s ? s : e.getStatusText();
            throw new InferenceException(detail, true, e);
        } catch (RestClientException e) {
            log.warn("Inference call {} failed: {}", path, e.getMessage());
            String detail = e instanceof HttpServerErrorException se
                    ? serverDetail(se) : "Inference service unavailable";
            throw new InferenceException(detail, false, e);
        }
    }

    private static String serverDetail(HttpServerErrorException e) {
        try {
            ErrorBody body = e.getResponseBodyAs(ErrorBody.class);
            if (body != null && body.detail() instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "Inference service unavailable";
    }
}
