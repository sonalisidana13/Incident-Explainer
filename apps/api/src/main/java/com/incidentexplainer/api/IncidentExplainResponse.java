package com.incidentexplainer.api;

import java.util.List;

public record IncidentExplainResponse(
    String summary,
    List<LikelyCause> likelyCauses,
    List<FirstCheck> firstChecks,
    List<String> nextDataToFetch,
    DebugInfo debug
) {

    public record LikelyCause(
        String title,
        double confidence,
        String reason,
        List<Citation> citations
    ) {}

    public record FirstCheck(
        String step,
        String why,
        List<Citation> citations
    ) {}

    public record Citation(
        String type,
        String source,
        long chunkId,
        String snippet
    ) {}

    public record DebugInfo(
        List<RetrievedChunkDebug> retrievedChunks
    ) {}

    public record RetrievedChunkDebug(
        long chunkId,
        String source,
        String title,
        double score
    ) {}
}
