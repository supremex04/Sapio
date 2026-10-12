package com.ved.common.inference;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

import com.ved.common.inference.InferenceException.Kind;
import com.ved.common.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * Shared HTTP client for the internal Python inference service: auth header, timeouts, request-id propagation
 * and decoding of the error envelope into a typed {@link InferenceException}. Feature adapters (extraction,
 * knowledge AI, study AI) build on it. Contract: contracts/inference-openapi.json, docs/specs/inference-api.md.
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
                .requestInterceptor((request, body, execution) -> {
                    RequestId.current().ifPresent(id -> request.getHeaders().set(RequestId.HEADER, id));
                    return execution.execute(request, body);
                })
                .build();
    }

    /** The error envelope every non-2xx inference response carries. */
    record ErrorBody(String code, String message, Boolean retryable, String requestId) {
    }

    public <T> T getJson(String path, Class<T> responseType) throws InferenceException {
        return call(path, () -> client.get().uri(path).retrieve().body(responseType));
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
        InferenceException failure;
        try {
            T response = request.get();
            if (response != null) {
                return response;
            }
            failure = new InferenceException(Kind.CONTRACT_VIOLATION, "EMPTY_RESPONSE",
                    "Empty response from the inference service", false, null, null, null);
        } catch (RestClientResponseException e) {
            failure = fromResponse(e);
        } catch (HttpMessageNotReadableException | UnknownContentTypeException e) {
            failure = new InferenceException(Kind.CONTRACT_VIOLATION, "UNREADABLE_RESPONSE",
                    "Inference response does not match the contract: " + e.getMessage(), false, null, null, e);
        } catch (RestClientException e) {
            // Connection refused, timeout, reset: the service is down or overloaded.
            failure = new InferenceException(Kind.UNAVAILABLE, "UNREACHABLE", "The AI service is unreachable right now",
                    true, null, null, e);
        }
        if (failure.kind().userSafe()) {
            log.warn("Inference {} failed: {} {} (request {})", path, failure.code(), failure.getMessage(),
                    failure.requestId().orElse("-"));
        } else {
            log.error("Inference {} failed: {} {} (request {})", path, failure.code(), failure.getMessage(),
                    failure.requestId().orElse("-"));
        }
        throw failure;
    }

    static InferenceException fromResponse(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        ErrorBody body = readBody(e);
        String code = body != null && body.code() != null ? body.code() : "HTTP_" + status.value();
        String message = body != null && body.message() != null && !body.message().isBlank()
                ? body.message() : e.getStatusText();
        Kind kind = kindOf(code, status);
        boolean retryable = body != null && body.retryable() != null ? body.retryable() : kind == Kind.UNAVAILABLE;
        return new InferenceException(kind, code, message, retryable, retryAfter(e), body == null ? null
                : body.requestId(), e);
    }

    private static Kind kindOf(String code, HttpStatusCode status) {
        return switch (code) {
            case "INPUT_REJECTED", "INPUT_TOO_LARGE" -> Kind.INPUT_REJECTED;
            case "MODEL_UNAVAILABLE" -> Kind.UNAVAILABLE;
            case "MISCONFIGURED", "UNAUTHORIZED" -> Kind.MISCONFIGURED;
            case "CONTRACT_VIOLATION", "NOT_FOUND", "METHOD_NOT_ALLOWED" -> Kind.CONTRACT_VIOLATION;
            case "INTERNAL_ERROR" -> Kind.INTERNAL;
            // No envelope (a proxy or an older service answered): fall back on the status.
            default -> switch (status.value()) {
                case 413, 422 -> Kind.INPUT_REJECTED;
                case 429, 502, 503, 504 -> Kind.UNAVAILABLE;
                case 401, 403 -> Kind.MISCONFIGURED;
                case 400, 404, 405 -> Kind.CONTRACT_VIOLATION;
                default -> Kind.INTERNAL;
            };
        };
    }

    private static ErrorBody readBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErrorBody.class);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private static Duration retryAfter(RestClientResponseException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        if (header == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.strip()));
        } catch (NumberFormatException httpDate) {
            return null;
        }
    }
}
