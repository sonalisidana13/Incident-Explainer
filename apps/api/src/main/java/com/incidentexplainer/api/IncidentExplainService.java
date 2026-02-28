package com.incidentexplainer.api;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IncidentExplainService {

    private static final int RETRIEVAL_K = 8;

    private final RetrievalService retrievalService;
    private final LlmProvider llmProvider;

    public IncidentExplainService(RetrievalService retrievalService, LlmProvider llmProvider) {
        this.retrievalService = retrievalService;
        this.llmProvider = llmProvider;
    }

    public IncidentExplainResponse explain(IncidentExplainRequest request) {
        IncidentExplainRequest.Alert alert = request.alert();
        List<String> logs = request.logs() == null ? List.of() : request.logs();
        Map<String, String> metrics = request.metrics() == null ? Map.of() : request.metrics();

        String retrievalQuery = buildRetrievalQuery(alert, logs, metrics);
        List<RetrievedChunk> retrievedChunks = retrievalService.searchDetailed(retrievalQuery, RETRIEVAL_K, null, null);

        return llmProvider.explain(request, retrievedChunks);
    }

    private String buildRetrievalQuery(
        IncidentExplainRequest.Alert alert,
        List<String> logs,
        Map<String, String> metrics
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(alert.name()).append(' ')
            .append(alert.service()).append(' ')
            .append(alert.env()).append(' ')
            .append(alert.description());

        if (!logs.isEmpty()) {
            sb.append(' ').append(String.join(" ", logs.stream().limit(20).toList()));
        }

        if (!metrics.isEmpty()) {
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                sb.append(' ').append(entry.getKey()).append(' ').append(entry.getValue());
            }
        }

        return sb.toString();
    }
}
