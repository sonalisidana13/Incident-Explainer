package com.incidentexplainer.api;

import java.util.List;

public interface LlmProvider {

    IncidentExplainResponse explain(IncidentExplainRequest request, List<RetrievedChunk> retrievedChunks);
}
