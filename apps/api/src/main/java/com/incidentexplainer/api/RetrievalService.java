package com.incidentexplainer.api;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final int DEFAULT_K = 5;
    private static final int MAX_K = 50;

    private final DeterministicEmbeddingService embeddingService;
    private final KbSearchRepository kbSearchRepository;

    public RetrievalService(
        DeterministicEmbeddingService embeddingService,
        KbSearchRepository kbSearchRepository
    ) {
        this.embeddingService = embeddingService;
        this.kbSearchRepository = kbSearchRepository;
    }

    public List<KbSearchResult> search(String query, Integer k, String kind, String service) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        int effectiveK = resolveK(k);
        String normalizedKind = normalize(kind);
        String normalizedService = normalize(service);

        float[] embedding = embeddingService.embed(query);
        String queryVectorLiteral = embeddingService.toVectorLiteral(embedding);

        return kbSearchRepository.search(queryVectorLiteral, effectiveK, normalizedKind, normalizedService);
    }

    private int resolveK(Integer k) {
        if (k == null) {
            return DEFAULT_K;
        }
        return Math.max(1, Math.min(k, MAX_K));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
