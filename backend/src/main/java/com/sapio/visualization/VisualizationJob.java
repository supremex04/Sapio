package com.sapio.visualization;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.sapio.document.storage.DocumentStorage;
import com.sapio.study.ai.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes one visualization: storyboard, scene code, then render. When a render fails because of the code (rejected,
 * Manim raised, too long, empty), the AI gets its code back with the renderer's error and repairs it, up to
 * {@code max-attempts} renders. The video and thumbnail are stored like any other file.
 */
@Component
class VisualizationJob {

    private static final Logger log = LoggerFactory.getLogger(VisualizationJob.class);
    /** Captions are written in English for now (the request itself may be in any language). */
    private static final String LANGUAGE = "en";

    private final VisualizationStore store;
    private final VisualizeAi ai;
    private final RendererClient renderer;
    private final DocumentStorage storage;
    private final Personalization personalization;
    private final VisualizationProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    VisualizationJob(VisualizationStore store, VisualizeAi ai, RendererClient renderer, DocumentStorage storage,
                     Personalization personalization, VisualizationProperties properties, JsonMapper json,
                     Clock clock) {
        this.store = store;
        this.ai = ai;
        this.renderer = renderer;
        this.storage = storage;
        this.personalization = personalization;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Async(VisualizationConfig.EXECUTOR)
    public void run(UUID id) {
        store.find(id).filter(r -> r.status().working()).ifPresent(this::make);
    }

    private void make(VisualizationStore.Row row) {
        UUID id = row.id();
        try {
            List<VisualizeAi.Beat> beats;
            String title;
            if (row.storyboardJson() == null) {
                VisualizeAi.Storyboard plan = ai.storyboard(new VisualizeAi.StoryboardSpec(row.request(), LANGUAGE,
                        row.context(), personalization.forLearner(row.userId()).learner()));
                if (!plan.feasible() || plan.beats() == null || plan.beats().isEmpty()) {
                    store.failed(id, plan.reason() == null || plan.reason().isBlank()
                            ? "This cannot be shown as an animation." : plan.reason(), clock.instant());
                    return;
                }
                beats = plan.beats();
                title = plan.title();
                store.storyboard(id, title, plan.kind(), json.writeValueAsString(beats), plan.model(), clock.instant());
            } else { // resumed after a restart
                beats = json.readValue(row.storyboardJson(), new TypeReference<List<VisualizeAi.Beat>>() { });
                title = row.title();
            }

            String code = row.code() != null ? row.code() : ai.code(new VisualizeAi.CodeSpec(title, beats, null, null)).code();
            String lastError = null;
            for (int attempt = row.attempts() + 1; attempt <= properties.maxAttempts(); attempt++) {
                store.code(id, code, clock.instant());
                try {
                    RendererClient.Rendered video = renderer.render(code);
                    String videoKey = storage.store(new ByteArrayInputStream(video.video()), "mp4");
                    String thumbKey = video.thumbnail().length == 0 ? null
                            : storage.store(new ByteArrayInputStream(video.thumbnail()), "png");
                    store.ready(id, videoKey, thumbKey, video.durationSeconds(), clock.instant());
                    return;
                } catch (RendererClient.RenderFailed e) {
                    if ("unavailable".equals(e.kind)) {
                        store.failed(id, "The animation studio is unavailable right now. Try again in a minute.",
                                clock.instant());
                        return;
                    }
                    lastError = e.getMessage();
                    store.attemptFailed(id, lastError, clock.instant());
                    log.info("Visualization {} render {} failed ({})", id, attempt, e.kind);
                    if (attempt < properties.maxAttempts()) {
                        code = ai.code(new VisualizeAi.CodeSpec(title, beats, code, lastError)).code();
                    }
                }
            }
            store.failed(id, "The animation could not be drawn after " + properties.maxAttempts()
                    + " tries. Try rephrasing the request or making it simpler.", clock.instant());
        } catch (VisualizationException e) {
            store.failed(id, e.getMessage(), clock.instant());
        } catch (RuntimeException e) {
            log.error("Visualization {} failed", id, e);
            store.failed(id, "Something went wrong while making this animation.", clock.instant());
        }
    }
}
