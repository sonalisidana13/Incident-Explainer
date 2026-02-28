package com.incidentexplainer.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KbSearchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KbSearchRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<KbSearchResult> search(String queryVectorLiteral, int k, String kind, String service) {
        return searchDetailed(queryVectorLiteral, k, kind, service).stream()
            .map(chunk -> new KbSearchResult(
                chunk.chunkId(),
                chunk.text(),
                chunk.metadata(),
                chunk.documentTitle(),
                chunk.documentSource()
            ))
            .toList();
    }

    public List<RetrievedChunk> searchDetailed(String queryVectorLiteral, int k, String kind, String service) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT c.id AS chunk_id,
                   c.text AS chunk_text,
                   c.metadata AS chunk_metadata,
                   d.title AS document_title,
                   d.source AS document_source,
                   (c.embedding <=> ?::vector) AS distance
            FROM chunks c
            JOIN documents d ON d.id = c.doc_id
            WHERE c.embedding IS NOT NULL
            """
        );

        List<Object> params = new ArrayList<>();
        params.add(queryVectorLiteral);

        if (hasText(kind)) {
            sql.append(" AND c.metadata->>'kind' = ?");
            params.add(kind);
        }

        if (hasText(service)) {
            sql.append(" AND c.metadata->>'service' = ?");
            params.add(service);
        }

        sql.append(" ORDER BY c.embedding <=> ?::vector LIMIT ?");
        params.add(queryVectorLiteral);
        params.add(k);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            double distance = rs.getDouble("distance");
            return new RetrievedChunk(
                rs.getLong("chunk_id"),
                rs.getString("chunk_text"),
                parseMetadata(rs.getObject("chunk_metadata")),
                rs.getString("document_title"),
                rs.getString("document_source"),
                toScore(distance)
            );
        });
    }

    private Map<String, Object> parseMetadata(Object rawValue) {
        if (rawValue == null) {
            return Map.of();
        }

        String json = rawValue.toString();
        if (json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double toScore(double distance) {
        return Math.max(0.0, Math.min(1.0, 1.0 - distance));
    }
}
