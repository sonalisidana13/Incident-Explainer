package com.incidentexplainer.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class KbIngestor {

    private static final int TARGET_CHUNK = 1000;
    private static final int MIN_CHUNK = 800;
    private static final int MAX_CHUNK = 1200;
    private static final int OVERLAP = 200;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingProvider embeddingProvider;

    public KbIngestor(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        EmbeddingProvider embeddingProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingProvider = embeddingProvider;
    }

    public void run() {
        Path kbRoot = resolveKbRoot();
        if (!Files.exists(kbRoot)) {
            throw new IllegalStateException("Knowledge base directory not found: " + kbRoot);
        }

        List<Path> files = listMarkdownFiles(kbRoot);
        int documentCount = 0;
        int chunkCount = 0;

        for (Path file : files) {
            int createdChunks = ingestFile(kbRoot, file);
            documentCount++;
            chunkCount += createdChunks;
        }

        System.out.printf(
            "KbIngestor complete at %s. documents=%d, chunks=%d, root=%s%n",
            OffsetDateTime.now(),
            documentCount,
            chunkCount,
            kbRoot
        );
    }

    private Path resolveKbRoot() {
        String fromEnv = System.getenv("KB_ROOT");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv).toAbsolutePath().normalize();
        }

        return Paths.get(System.getProperty("user.dir"), "data", "knowledge_base")
            .toAbsolutePath()
            .normalize();
    }

    private List<Path> listMarkdownFiles(Path kbRoot) {
        try (Stream<Path> stream = Files.walk(kbRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".md"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list markdown files in " + kbRoot, e);
        }
    }

    protected int ingestFile(Path kbRoot, Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading file: " + file, e);
        }

        String relPath = kbRoot.relativize(file).toString().replace('\\', '/');
        String kind = relPath.contains("/") ? relPath.substring(0, relPath.indexOf('/')) : "unknown";
        String title = extractTitle(content, file);
        String service = detectService(kind, file);

        jdbcTemplate.update("DELETE FROM chunks WHERE doc_id IN (SELECT id FROM documents WHERE source = ?)", relPath);
        jdbcTemplate.update("DELETE FROM documents WHERE source = ?", relPath);

        Long docId = insertDocument(title, relPath);

        List<String> chunks = splitIntoChunks(content);
        int chunkIndex = 0;
        for (String chunkText : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "knowledge_base");
            metadata.put("rel_path", relPath);
            metadata.put("kind", kind);
            if (service != null) {
                metadata.put("service", service);
            }

            String metadataJson = toJson(metadata);
            float[] embedding = embeddingProvider.embed(chunkText);
            String vectorLiteral = embeddingProvider.toVectorLiteral(embedding);

            jdbcTemplate.update(
                """
                INSERT INTO chunks (doc_id, chunk_index, text, metadata, embedding)
                VALUES (?, ?, ?, ?::jsonb, ?::vector)
                """,
                docId,
                chunkIndex,
                chunkText,
                metadataJson,
                vectorLiteral
            );
            chunkIndex++;
        }

        return chunks.size();
    }

    private Long insertDocument(String title, String source) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO documents (title, source) VALUES (?, ?)",
                new String[]{"id"}
            );
            statement.setString(1, title);
            statement.setString(2, source);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to fetch generated document id for source=" + source);
        }
        return key.longValue();
    }

    private String extractTitle(String content, Path file) {
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }

        String filename = file.getFileName().toString().replaceFirst("\\.md$", "");
        return filename.replace('-', ' ');
    }

    private String detectService(String kind, Path file) {
        if (!"services".equals(kind)) {
            return null;
        }

        String base = file.getFileName().toString().replaceFirst("\\.md$", "");
        if (base.endsWith("-service")) {
            return base;
        }
        return null;
    }

    private List<String> splitIntoChunks(String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return List.of("");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length()) {
            int end = chooseChunkEnd(normalized, start);
            chunks.add(normalized.substring(start, end).trim());

            if (end >= normalized.length()) {
                break;
            }

            int nextStart = Math.max(0, end - OVERLAP);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private int chooseChunkEnd(String text, int start) {
        int minEnd = Math.min(text.length(), start + MIN_CHUNK);
        int targetEnd = Math.min(text.length(), start + TARGET_CHUNK);
        int maxEnd = Math.min(text.length(), start + MAX_CHUNK);

        if (maxEnd == text.length()) {
            return text.length();
        }

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = minEnd; i <= maxEnd; i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                int distance = Math.abs(i - targetEnd);
                if (distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
        }

        if (best != -1) {
            return best;
        }

        return maxEnd;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metadata json", e);
        }
    }
}
