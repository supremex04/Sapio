package com.sapio.common.inference;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sapio.common.web.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real HTTP path against a stub inference server. */
class InferenceClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseJson = "";
    private volatile String retryAfter;
    private final AtomicReference<String> receivedRequestId = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedApiKey.set(exchange.getRequestHeaders().getFirst(InferenceClient.API_KEY_HEADER));
        receivedRequestId.set(exchange.getRequestHeaders().getFirst(RequestId.HEADER));
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private InferenceClient client() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new InferenceClient(RestClient.builder(),
                new InferenceProperties(base, "secret-key", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofSeconds(10)));
    }

    record Echo(String verdict, double score) {
    }

    @Test
    void postsCamelCaseJsonWithApiKeyAndParsesResponse() throws Exception {
        responseJson = """
                {"verdict":"CORRECT","score":0.9,"ignored":true}""";

        Echo echo = client().postJson("/v1/study/grade", Map.of("referenceAnswer", "R"), Echo.class);

        assertThat(echo).isEqualTo(new Echo("CORRECT", 0.9));
        assertThat(receivedApiKey.get()).isEqualTo("secret-key");
        assertThat(receivedBody.get()).contains("\"referenceAnswer\":\"R\"");
    }

    @Test
    void rejectedInputCarriesTheUserSafeMessage() {
        status = 422;
        responseJson = """
                {"code":"INPUT_REJECTED","message":"Unreadable or corrupt PDF","retryable":false,"requestId":"r1"}""";

        assertThatThrownBy(() -> client().postJson("/x", Map.of(), Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(InferenceException.Kind.INPUT_REJECTED);
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.userMessage("fallback")).isEqualTo("Unreadable or corrupt PDF");
                    assertThat(e.requestId()).contains("r1");
                });
    }

    @Test
    void unavailableModelsAreRetryableWithTheServersHint() {
        status = 503;
        retryAfter = "3600";
        responseJson = """
                {"code":"MODEL_UNAVAILABLE","message":"The AI service is busy right now.","retryable":true}""";

        assertThatThrownBy(() -> client().postJson("/x", Map.of(), Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(InferenceException.Kind.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.retryAfter()).contains(Duration.ofHours(1));
                    assertThat(e.userMessage("fallback")).contains("busy");
                });
    }

    @Test
    void operatorDetailsNeverBecomeUserMessages() {
        status = 500;
        responseJson = """
                {"code":"MISCONFIGURED","message":"set the API key in $SAPIO_INFERENCE_GEMINI_API_KEY","retryable":false}""";

        assertThatThrownBy(() -> client().postJson("/x", Map.of(), Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(InferenceException.Kind.MISCONFIGURED);
                    assertThat(e.userMessage("The AI is unavailable")).isEqualTo("The AI is unavailable");
                });
    }

    @Test
    void wrongInternalKeyIsMisconfigurationNotRejectedInput() {
        status = 401;
        responseJson = """
                {"code":"UNAUTHORIZED","message":"Invalid internal API key","retryable":false}""";

        assertThatThrownBy(() -> client().getJson("/x", Echo.class))
                .isInstanceOfSatisfying(InferenceException.class,
                        e -> assertThat(e.kind()).isEqualTo(InferenceException.Kind.MISCONFIGURED));
    }

    @Test
    void withoutAnEnvelopeTheStatusDecides() {
        status = 502;
        responseJson = "<html>Bad gateway</html>";

        assertThatThrownBy(() -> client().getJson("/x", Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(InferenceException.Kind.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void anUnreachableServiceIsRetryable() {
        server.stop(0);

        assertThatThrownBy(() -> client().getJson("/x", Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(InferenceException.Kind.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void theRequestIdTravelsWithTheCall() throws Exception {
        responseJson = """
                {"verdict":"CORRECT","score":1}""";
        MDC.put(RequestId.MDC_KEY, "req-42");
        try {
            client().postJson("/x", Map.of(), Echo.class);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
        assertThat(receivedRequestId.get()).isEqualTo("req-42");
    }
}
