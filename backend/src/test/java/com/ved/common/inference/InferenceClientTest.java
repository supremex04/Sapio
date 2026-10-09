package com.ved.common.inference;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private InferenceClient client() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new InferenceClient(RestClient.builder(),
                new InferenceProperties(base, "secret-key", Duration.ofSeconds(2), Duration.ofSeconds(5)));
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
    void clientErrorsCarryTheServiceDetail() {
        status = 422;
        responseJson = """
                {"detail":"Unreadable or corrupt PDF"}""";

        assertThatThrownBy(() -> client().postJson("/x", Map.of(), Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.isClientError()).isTrue();
                    assertThat(e.getMessage()).isEqualTo("Unreadable or corrupt PDF");
                });
    }

    @Test
    void serverErrorsAreRetryableAndKeepDetail() {
        status = 503;
        responseJson = """
                {"detail":"The AI service is busy right now."}""";

        assertThatThrownBy(() -> client().postJson("/x", Map.of(), Echo.class))
                .isInstanceOfSatisfying(InferenceException.class, e -> {
                    assertThat(e.isClientError()).isFalse();
                    assertThat(e.getMessage()).contains("busy");
                });
    }
}
