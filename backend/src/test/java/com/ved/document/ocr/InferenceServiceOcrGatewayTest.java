package com.ved.document.ocr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real HTTP/multipart path against a stub inference server. */
class InferenceServiceOcrGatewayTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseJson = "";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/ocr", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedApiKey.set(exchange.getRequestHeaders().getFirst(InferenceServiceOcrGateway.API_KEY_HEADER));
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private InferenceServiceOcrGateway gateway() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new InferenceServiceOcrGateway(RestClient.builder(),
                new InferenceProperties(base, "secret-key", Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    private static OcrRequest request() {
        return new OcrRequest(new ByteArrayResource("%PDF-1.7 fake".getBytes()), "notes.pdf", "application/pdf");
    }

    @Test
    void sendsMultipartFileWithApiKeyAndParsesResponse() throws Exception {
        responseJson = """
                {"markdown":"# Notes","engine":"rapidocr","pageCount":2}""";

        OcrResult result = gateway().extract(request());

        assertThat(result).isEqualTo(new OcrResult("# Notes", "rapidocr", 2));
        assertThat(receivedApiKey.get()).isEqualTo("secret-key");
        assertThat(receivedBody.get())
                .contains("name=\"file\"")
                .contains("filename=\"notes.pdf\"")
                .contains("Content-Type: application/pdf")
                .contains("%PDF-1.7 fake");
    }

    @Test
    void clientErrorsCarryTheServiceDetail() {
        status = 422;
        responseJson = """
                {"detail":"Unreadable or corrupt PDF"}""";

        assertThatThrownBy(() -> gateway().extract(request()))
                .isInstanceOf(OcrException.class)
                .hasMessage("Document could not be processed: Unreadable or corrupt PDF");
    }

    @Test
    void serverErrorsAreReportedAsUnavailable() {
        status = 503;
        responseJson = """
                {"detail":"model down"}""";

        assertThatThrownBy(() -> gateway().extract(request()))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("unavailable");
    }
}
