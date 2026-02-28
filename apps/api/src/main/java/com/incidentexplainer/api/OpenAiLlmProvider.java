package com.incidentexplainer.api;

import java.util.List;

public class OpenAiLlmProvider implements LlmProvider {

    private final String model;

    public OpenAiLlmProvider(String model) {
        this.model = model;
    }

    @Override
    public IncidentExplainResponse explain(IncidentExplainRequest request, List<RetrievedChunk> retrievedChunks) {
        // TODO: Implement OpenAI chat/completions call.
        // Suggested contract:
        // 1) Build prompt from request + retrieved chunks.
        // 2) Request strict JSON output matching IncidentExplainResponse.
        // 3) Validate required citations and confidence bounds before returning.
        throw new UnsupportedOperationException(
            "OpenAiLlmProvider is a skeleton. Configure LLM_PROVIDER=heuristic or implement API integration for model=" + model
        );
    }

    public String model() {
        return model;
    }

    public String buildPrompt(IncidentExplainRequest request, List<RetrievedChunk> retrievedChunks) {
        // TODO: Build deterministic prompt template for model invocation.
        throw new UnsupportedOperationException("Prompt builder not implemented");
    }
}
