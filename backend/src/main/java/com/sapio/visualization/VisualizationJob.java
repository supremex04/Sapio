package com.sapio.visualization;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.sapio.document.storage.DocumentStorage;
import com.sapio.study.ai.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes one visualization. The storyboard call plans it and, when the rules did not already decide, picks the engine
 * (docs/adr/0017-visualization-engines.md):
 * <ul>
 *   <li>animation: scene code, then render; a failed render goes back to the AI with the error, up to
 *       {@code max-attempts} renders;</li>
 *   <li>molecule, protein, code: no code is written. RDKit builds the molecule, the Protein Data Bank supplies the
 *       structure, or the renderer traces the program; then the captions are spoken into one narration track that
 *       the viewer follows. The learner's own SMILES, PDB ID or program is preferred over the AI's.</li>
 * </ul>
 */
@Component
class VisualizationJob {

    private static final Logger log = LoggerFactory.getLogger(VisualizationJob.class);
    /** Captions are written in English for now (the request itself may be in any language). */
    private static final String LANGUAGE = "en";
    private static final String UNAVAILABLE = "The animation studio is unavailable right now. Try again in a minute.";

    /** The engine's data from the storyboard, plus the learner's own (from the request) when they gave some. */
    record Plan(VisualizeAi.Molecule molecule, VisualizeAi.Protein protein, String program, String learnerData) {
    }

    private record Planned(VisualizationEngine engine, String title, List<VisualizeAi.Beat> beats, Plan plan) {
    }

    private final VisualizationStore store;
    private final VisualizeAi ai;
    private final RendererClient renderer;
    private final ProteinBank proteinBank;
    private final DocumentStorage storage;
    private final Personalization personalization;
    private final VisualizationProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    VisualizationJob(VisualizationStore store, VisualizeAi ai, RendererClient renderer, ProteinBank proteinBank,
                     DocumentStorage storage, Personalization personalization, VisualizationProperties properties,
                     JsonMapper json, Clock clock) {
        this.store = store;
        this.ai = ai;
        this.renderer = renderer;
        this.proteinBank = proteinBank;
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
            Optional<Planned> planned = row.storyboardJson() == null ? plan(row) : Optional.of(resumed(row));
            if (planned.isEmpty()) {
                return;
            }
            if (planned.get().engine() == VisualizationEngine.ANIMATION) {
                animate(row, planned.get());
            } else {
                build(row, planned.get());
            }
        } catch (VisualizationException e) {
            store.failed(id, e.getMessage(), clock.instant());
        } catch (RendererClient.RenderFailed e) {
            store.failed(id, UNAVAILABLE, clock.instant());
        } catch (RuntimeException e) {
            log.error("Visualization {} failed", id, e);
            store.failed(id, "Something went wrong while making this visualization.", clock.instant());
        }
    }

    // ------------------------------------------------------------------------------------------------ planning

    private Optional<Planned> plan(VisualizationStore.Row row) throws VisualizationException {
        Optional<EngineRouter.Routed> routed = EngineRouter.route(row.request()).filter(this::confirmed);
        VisualizationEngine hint = row.engineChoice() != VisualizationEngine.AUTO ? row.engineChoice()
                : routed.map(EngineRouter.Routed::engine).orElse(VisualizationEngine.AUTO);
        VisualizeAi.Storyboard board = ai.storyboard(new VisualizeAi.StoryboardSpec(row.request(), LANGUAGE,
                row.context(), personalization.forLearner(row.userId()).learner(), hint.wire()));
        if (!board.feasible() || board.beats() == null || board.beats().isEmpty()) {
            store.failed(row.id(), board.reason() == null || board.reason().isBlank()
                    ? "This cannot be shown as a visualization." : board.reason(), clock.instant());
            return Optional.empty();
        }
        VisualizationEngine engine = VisualizationEngine.fromWire(board.engine());
        String learnerData = routed.filter(r -> r.engine() == engine).map(EngineRouter.Routed::data).orElse(null);
        Plan plan = new Plan(board.molecule(), board.protein(), board.program(), learnerData);
        store.storyboard(row.id(), engine, board.title(), board.kind(), json.writeValueAsString(board.beats()),
                json.writeValueAsString(plan), board.model(), clock.instant());
        return Optional.of(new Planned(engine, board.title(), board.beats(), plan));
    }

    /** A SMILES candidate counts only once RDKit reads it (so "E=mc2" is not a molecule). */
    private boolean confirmed(EngineRouter.Routed routed) {
        if (routed.engine() != VisualizationEngine.MOLECULE) {
            return true;
        }
        try {
            renderer.molecule(routed.data(), List.of(), List.of());
            return true;
        } catch (RendererClient.RenderFailed e) {
            return false;
        }
    }

    private Planned resumed(VisualizationStore.Row row) {
        List<VisualizeAi.Beat> beats = json.readValue(row.storyboardJson(), new TypeReference<>() { });
        Plan plan = row.planJson() == null ? new Plan(null, null, null, null) : json.readValue(row.planJson(), Plan.class);
        return new Planned(row.engine() == null ? VisualizationEngine.ANIMATION : row.engine(), row.title(), beats, plan);
    }

    // ---------------------------------------------------------------------------------------------- animation

    private void animate(VisualizationStore.Row row, Planned planned) throws VisualizationException {
        UUID id = row.id();
        List<VisualizeAi.Beat> beats = planned.beats();
        String code = row.code() != null ? row.code()
                : ai.code(new VisualizeAi.CodeSpec(planned.title(), beats, null, null)).code();
        List<String> narration = says(beats);
        for (int attempt = row.attempts() + 1; attempt <= properties.maxAttempts(); attempt++) {
            store.code(id, code, clock.instant());
            try {
                RendererClient.Rendered video = renderer.render(code, narration);
                String videoKey = storage.store(new ByteArrayInputStream(video.video()), "mp4");
                String thumbKey = video.thumbnail().length == 0 ? null
                        : storage.store(new ByteArrayInputStream(video.thumbnail()), "png");
                store.ready(id, videoKey, thumbKey, video.durationSeconds(), video.narrated(), clock.instant());
                return;
            } catch (RendererClient.RenderFailed e) {
                if ("unavailable".equals(e.kind)) {
                    store.failed(id, UNAVAILABLE, clock.instant());
                    return;
                }
                store.attemptFailed(id, e.getMessage(), clock.instant());
                log.info("Visualization {} render {} failed ({})", id, attempt, e.kind);
                if (attempt < properties.maxAttempts()) {
                    code = ai.code(new VisualizeAi.CodeSpec(planned.title(), beats, code, e.getMessage())).code();
                }
            }
        }
        store.failed(id, "The animation could not be drawn after " + properties.maxAttempts()
                + " tries. Try rephrasing the request or making it simpler.", clock.instant());
    }

    // ------------------------------------------------------------------------------- molecule, protein, code

    private void build(VisualizationStore.Row row, Planned planned) throws RendererClient.RenderFailed {
        Plan plan = planned.plan();
        Map<String, Object> scene = new LinkedHashMap<>();
        String thumbKey = null;
        String structureKey = null;
        switch (planned.engine()) {
            case MOLECULE -> {
                List<String> focus = planned.beats().stream().map(b -> b.focus() == null ? "" : b.focus()).toList();
                RendererClient.Molecule molecule = null;
                String error = "No molecule was given.";
                for (String smiles : candidates(plan.learnerData(), plan.molecule() == null ? null
                        : plan.molecule().smiles())) {
                    try {
                        molecule = renderer.molecule(smiles, focus, says(planned.beats()));
                        break;
                    } catch (RendererClient.RenderFailed e) {
                        if ("unavailable".equals(e.kind)) {
                            throw e;
                        }
                        error = e.getMessage();
                    }
                }
                if (molecule == null) {
                    store.failed(row.id(), "Could not build the molecule: " + error, clock.instant());
                    return;
                }
                thumbKey = storage.store(new ByteArrayInputStream(Base64.getDecoder().decode(molecule.pngBase64())),
                        "png");
                scene.put("name", plan.molecule() != null ? plan.molecule().name() : planned.title());
                scene.put("smiles", molecule.canonicalSmiles());
                scene.put("formula", molecule.formula());
                scene.put("weight", molecule.weight());
                scene.put("heavyAtoms", molecule.heavyAtoms());
                scene.put("threeD", molecule.threeD());
                scene.put("molBlock", molecule.molBlock());
                scene.put("highlights", molecule.highlights());
                scene.put("drawings", molecule.drawingsBase64()); // 2D, per beat: for browsers without WebGL
            }
            case PROTEIN -> {
                ProteinBank.Entry entry = null;
                String error = "No Protein Data Bank ID was given.";
                for (String pdbId : candidates(plan.learnerData(), plan.protein() == null ? null
                        : plan.protein().pdbId())) {
                    try {
                        entry = proteinBank.fetch(pdbId);
                        break;
                    } catch (ProteinBank.NotFound e) {
                        error = e.getMessage();
                    }
                }
                if (entry == null) {
                    store.failed(row.id(), error + ".", clock.instant());
                    return;
                }
                structureKey = storage.store(new ByteArrayInputStream(entry.structure()), "cif");
                if (entry.image() != null) {
                    thumbKey = storage.store(new ByteArrayInputStream(entry.image()), "jpg");
                }
                scene.put("pdbId", entry.id());
                scene.put("name", plan.protein() != null ? plan.protein().name() : planned.title());
                scene.put("entryTitle", entry.title());
            }
            case CODE -> {
                JsonNode trace = null;
                String program = null;
                String error = "No program was given.";
                for (String candidate : candidates(plan.learnerData(), plan.program())) {
                    try {
                        trace = renderer.trace(candidate, List.of());
                        program = candidate;
                        break;
                    } catch (RendererClient.RenderFailed e) {
                        if ("unavailable".equals(e.kind)) {
                            throw e;
                        }
                        error = e.getMessage();
                    }
                }
                if (trace == null) {
                    store.failed(row.id(), "The program could not be run: " + error, clock.instant());
                    return;
                }
                scene.put("program", program);
                scene.put("trace", trace);
            }
            default -> throw new IllegalStateException("Not a scene engine: " + planned.engine());
        }

        RendererClient.Narration narration = renderer.narrate(says(planned.beats()));
        String audioKey = narration.audio() == null ? null
                : storage.store(new ByteArrayInputStream(narration.audio()), "m4a");
        scene.put("timing", narration.beats());
        RendererClient.Timing last = narration.beats().getLast();
        store.ready(row.id(), new VisualizationStore.Scene(json.writeValueAsString(scene), audioKey, structureKey,
                thumbKey, last.start() + last.seconds(), audioKey != null), clock.instant());
    }

    private static List<String> says(List<VisualizeAi.Beat> beats) {
        return beats.stream().map(VisualizeAi.Beat::says).toList();
    }

    /** The learner's own data first, then the AI's; blanks and repeats left out. */
    private static List<String> candidates(String... values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v);
            }
        }
        return new ArrayList<>(out);
    }
}
