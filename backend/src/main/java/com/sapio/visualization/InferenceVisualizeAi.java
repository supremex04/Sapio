package com.sapio.visualization;

import com.sapio.common.inference.InferenceClient;
import com.sapio.common.inference.InferenceException;
import org.springframework.stereotype.Component;

@Component
class InferenceVisualizeAi implements VisualizeAi {

    private static final String UNAVAILABLE = "The AI is unavailable right now, please retry in a moment";

    private final InferenceClient inference;

    InferenceVisualizeAi(InferenceClient inference) {
        this.inference = inference;
    }

    @Override
    public Storyboard storyboard(StoryboardSpec spec) throws VisualizationException {
        return post("/v1/visualize/storyboard", spec, Storyboard.class);
    }

    @Override
    public SceneCode code(CodeSpec spec) throws VisualizationException {
        return post("/v1/visualize/code", spec, SceneCode.class);
    }

    @Override
    public CircuitRepair repairCircuit(CircuitRepairSpec spec) throws VisualizationException {
        return post("/v1/visualize/circuit", spec, CircuitRepair.class);
    }

    private <T> T post(String path, Object body, Class<T> type) throws VisualizationException {
        try {
            return inference.postJson(path, body, type);
        } catch (InferenceException e) {
            throw new VisualizationException(e.userMessage(UNAVAILABLE), e.isRetryable(), e);
        }
    }
}
