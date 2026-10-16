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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client of the renderer service (renderer/): scene code in, an MP4 and a thumbnail out; also molecules (RDKit),
 * program traces and narration tracks for the other engines.
 */
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

    private record MoleculeRequest(String smiles, List<String> highlights, List<String> captions) {
    }

    /**
     * A molecule built by RDKit; {@code highlights}: per beat, the atoms its focus matches; {@code drawingsBase64}:
     * per beat, the 2D drawing with them highlighted (shown when the browser has no WebGL for the 3D view).
     */
    record Molecule(String canonicalSmiles, String formula, double weight, int heavyAtoms, String molBlock,
                    boolean threeD, String pngBase64, List<List<Integer>> highlights, List<String> drawingsBase64) {
    }

    private record TraceRequest(String code, List<String> stdin) {
    }

    private record NarrateRequest(List<String> lines) {
    }

    record Timing(double start, double seconds) {
    }

    private record NarrateResponse(String audioBase64, List<Timing> beats) {
    }

    /** {@code audio}: AAC in MP4, or null when speech is unavailable (timings are then estimated). */
    record Narration(byte[] audio, List<Timing> beats) {
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
    /**
     * @param highlights each beat's focus (SMARTS or "group:name")
     * @param captions   each beat's narration: a group it names rescues a focus that lights up nothing or everything
     * @throws RenderFailed kind {@code input} when RDKit cannot read the SMILES
     */
    Molecule molecule(String smiles, List<String> highlights, List<String> captions) throws RenderFailed {
        return post("/v1/molecule", new MoleculeRequest(smiles, highlights, captions), Molecule.class);
    }

    /** The trace as the renderer returns it: {@code {steps, output, error, truncated}}. */
    JsonNode trace(String code, List<String> stdin) throws RenderFailed {
        return post("/v1/trace", new TraceRequest(code, stdin), JsonNode.class);
    }

    Narration narrate(List<String> lines) throws RenderFailed {
        NarrateResponse response = post("/v1/narrate", new NarrateRequest(lines), NarrateResponse.class);
        return new Narration(response.audioBase64() == null ? null : Base64.getDecoder().decode(response.audioBase64()),
                response.beats());
    }

    private <T> T post(String path, Object body, Class<T> type) throws RenderFailed {
        try {
            T response = client.post().uri(path).body(body).retrieve().body(type);
            if (response == null) {
                throw new RenderFailed("unavailable", "The renderer returned nothing");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw failure(e);
        } catch (RestClientException e) {
            log.warn("Renderer unreachable: {}", e.getMessage());
            throw new RenderFailed("unavailable", "The renderer is unavailable right now");
        }
    }

    private RenderFailed failure(RestClientResponseException e) {
        if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(422))) {
            RenderError error = json.readValue(e.getResponseBodyAsString(), RenderError.class);
            return new RenderFailed(error.kind(), error.message());
        }
        log.warn("Renderer answered {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new RenderFailed("unavailable", "The renderer is unavailable right now");
    }

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
            throw failure(e);
        } catch (RestClientException e) {
            log.warn("Renderer unreachable: {}", e.getMessage());
            throw new RenderFailed("unavailable", "The renderer is unavailable right now");
        }
    }
}
