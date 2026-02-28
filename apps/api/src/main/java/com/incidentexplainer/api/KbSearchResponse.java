package com.incidentexplainer.api;

import java.util.List;

public record KbSearchResponse(
    String query,
    int k,
    String kind,
    String service,
    List<KbSearchResult> results
) {}
