package com.incidentexplainer.api;

import java.util.Map;

public record RetrievedChunk(
    long chunkId,
    String text,
    Map<String, Object> metadata,
    String documentTitle,
    String documentSource,
    double score
) {}
