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
        StringBuilder sql = new StringBuilder(
            """
            SELECT c.id AS chunk_id,
                   c.text AS chunk_text,
                   c.metadata AS chunk_metadata,
                   d.title AS document_title,
                   d.source AS document_source
            FROM chunks c
            JOIN documents d ON d.id = c.doc_id
            WHERE c.embedding IS NOT NULL
            """
        );

        List<Object> params = new ArrayList<>();

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

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) ->
            new KbSearchResult(
                rs.getLong("chunk_id"),
                rs.getString("chunk_text"),
                parseMetadata(rs.getObject("chunk_metadata")),
                rs.getString("document_title"),
                rs.getString("document_source")
            )
        );
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
}
