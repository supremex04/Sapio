package com.sapio.visualization;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.sapio.common.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/** Client of the renderer service (renderer/): scene code in, an MP4 and a thumbnail out. */
@Component
class RendererClient {

    private static final Logger log = LoggerFactory.getLogger(RendererClient.class);

    /** {@code narrated}: the video has spoken narration. */
    record Rendered(byte[] video, byte[] thumbnail, double durationSeconds, boolean narrated) {
    }

    /**
     * The scene did not render. {@code kind}: code (rejected before running), error (Manim raised), timeout, output
     * (no usable video): all are fed back to the AI; {@code unavailable}: the renderer itself is down.
     */
    static class RenderFailed extends Exception {
        final String kind;

        RenderFailed(String kind, String message) {
            super(message);
            this.kind = kind;
        }
    }

    private record RenderRequest(String code, String quality, List<String> narration) {
    }

    private record RenderResponse(String videoBase64, String thumbnailBase64, double durationSeconds,
                                  boolean narrated) {
    }

    private record RenderError(String kind, String message) {
    }

    private final RestClient client;
    private final JsonMapper json;

    RendererClient(RestClient.Builder builder, VisualizationProperties properties, JsonMapper json) {
        this.json = json;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.renderTimeout());
        this.client = builder.clone()
                .baseUrl(properties.rendererUrl().toString())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", properties.rendererApiKey())
                .requestInterceptor((request, body, execution) -> {
                    RequestId.current().ifPresent(id -> request.getHeaders().set(RequestId.HEADER, id));
                    return execution.execute(request, body);
                })
                .build();
    }

    /** @param narration the spoken line of each beat, in order (the scene plays them with narrate) */
    Rendered render(String code, List<String> narration) throws RenderFailed {
        try {
            RenderResponse response = client.post().uri("/v1/render").body(new RenderRequest(code, "final", narration))
                    .retrieve().body(RenderResponse.class);
            if (response == null || response.videoBase64() == null) {
                throw new RenderFailed("unavailable", "The renderer returned nothing");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            return new Rendered(b64.decode(response.videoBase64()),
                    response.thumbnailBase64() == null ? new byte[0] : b64.decode(response.thumbnailBase64()),
                    response.durationSeconds(), response.narrated());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(422))) {
                RenderError error = json.readValue(e.getResponseBodyAsString(), RenderError.class);
                throw new RenderFailed(error.kind(), error.message());
            }
            log.warn("Renderer answered {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RenderFailed("unavailable", "The renderer is unavailable right now");
        } catch (RestClientException e) {
            log.warn("Renderer unreachable: {}", e.getMessage());
            throw new RenderFailed("unavailable", "The renderer is unavailable right now");
        }
    }
}
