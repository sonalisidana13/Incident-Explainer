# Incident Explainer: Beginner Project Guide

This document explains the project end-to-end in beginner-friendly language.

---

## 1) What This Project Is

Incident Explainer is a backend system that:

1. Stores operational knowledge (runbooks/postmortems/service notes) in a database.
2. Converts that text into vectors (numbers) for semantic search.
3. Retrieves the most relevant knowledge for a live incident.
4. Returns a deterministic explanation JSON with:
   - summary
   - likely causes
   - first checks
   - citations
   - next data to fetch

No external LLM is required in default mode.

---

## 2) Big Picture Architecture

```text
Knowledge Base Markdown (data/knowledge_base/*.md)
                |
                v
        KbIngestor (Java CLI)
                |
                v
   Postgres + pgvector (documents, chunks, embedding)
                |
                v
      RetrievalService + pgvector search
                |
                v
     NoopHeuristicExplainer (deterministic rules)
                |
                v
        POST /incident/explain response JSON
```

---

## 3) Where The App Starts

There are 2 main entry points:

1. API server start:
- Class: `IncidentExplainerApiApplication`
- Command: `gradle :apps:api:bootRun`
- Purpose: run web API endpoints (`/health`, `/kb/search`, `/incident/explain`)

2. Ingestion CLI start:
- Class: `KbIngestorApplication`
- Command: `gradle :apps:api:ingestKnowledgeBase`
- Purpose: read markdown KB files and write `documents/chunks` rows

---

## 4) Important Folders (What Matters Most)

### Runtime Core
- `apps/api/src/main/java/com/incidentexplainer/api`
  - all Spring Boot Java code

### Database
- `infra/docker-compose.yml`
  - local Postgres container with pgvector image
- `apps/api/src/main/resources/db/migration/V1__init.sql`
  - Flyway migration creating schema and indexes

### Data Inputs
- `data/knowledge_base`
  - source markdown files for ingestion
- `data/incidents`
  - sample incident request payloads

### Docs
- `README.md`
  - command reference
- `docs/START_HERE.md`
  - quick run path
- `docs/PROJECT_GUIDE.md` (this file)

### Note about legacy files
There are older scaffold leftovers (for example `apps/api/pom.xml`, `apps/api/package.json`, `apps/api/src/index.js`).
Current active backend path is **Gradle + Spring Boot Java sources under `src/main/java`**.

---

## 5) Database Schema Explained

Migration creates:

1. `documents`
- one row per markdown file
- columns:
  - `id`
  - `title`
  - `source` (relative file path)
  - `created_at`

2. `chunks`
- each document is split into multiple chunks
- columns:
  - `id`
  - `doc_id` (FK to documents)
  - `chunk_index`
  - `text`
  - `metadata` (jsonb: kind, rel_path, service, etc.)
  - `embedding` (`vector(384)`)

Indexes:
- `idx_chunks_doc_id`
- `idx_chunks_embedding_ivfflat` (vector ANN index)

---

## 6) What Is a Vector? (Beginner Explanation)

A vector here is just a list of numbers, like:

```text
[0.02, -0.11, 0.04, ... 384 values total]
```

Why use this?
- Plain keyword search only matches exact words.
- Vector search helps find semantically similar text.
- Example:
  - Query: “db pool timeout”
  - It can still match KB text like “connection is not available” even if exact words differ.

How this project makes vectors:
- `FakeEmbeddingProvider` creates deterministic 384-dim vectors from token hashes.
- “Deterministic” means same input text always gives same vector.

How search works:
- pgvector operator `<=>` computes distance between vectors.
- Smaller distance = more similar.
- SQL orders by nearest vectors and returns top-k chunks.

---

## 7) Ingestion Flow (KB -> DB)

Class: `KbIngestor`

Steps:
1. Resolve KB path (`KB_ROOT` env or default `data/knowledge_base`).
2. Recursively find all `.md` files.
3. For each file:
   - extract title
   - detect kind (`runbooks/postmortems/services`)
   - split into overlapping chunks (~800-1200 chars, overlap 200)
   - generate embedding for each chunk
   - upsert by source path (delete old rows, insert fresh rows)
4. Write document/chunk rows into Postgres.

Output data powers `/kb/search` and `/incident/explain`.

---

## 8) Retrieval Flow (`/kb/search`)

Main classes:
- `KbSearchController`
- `RetrievalService`
- `KbSearchRepository`

Flow:
1. HTTP request: `GET /kb/search?q=...&k=...&kind=...&service=...`
2. `RetrievalService`:
   - validates query
   - converts query text into embedding vector
3. `KbSearchRepository` executes SQL:
   - optional JSONB filters (`kind`, `service`)
   - vector ordering: `ORDER BY c.embedding <=> ?::vector LIMIT ?`
4. Returns chunk text + metadata + document info.

---

## 9) Incident Explain Flow (`/incident/explain`)

Main classes:
- `IncidentExplainController`
- `IncidentExplainService`
- `LlmProvider` abstraction
- `NoopHeuristicExplainer` (default)

Flow:
1. Client sends alert/logs/metrics/deploys JSON.
2. `IncidentExplainService` builds a retrieval query from those fields.
3. It retrieves top KB chunks (`RetrievalService.searchDetailed`).
4. It calls active `LlmProvider`.
5. In default mode, `NoopHeuristicExplainer`:
   - applies deterministic keyword/rule scoring
   - builds likely causes
   - ensures citations exist
   - creates first checks
   - adds `nextDataToFetch` when evidence is weak
6. Returns strict response JSON.

---

## 10) Provider Abstraction (Swappable Components)

### Embeddings
- Interface: `EmbeddingProvider`
- Implementations:
  - `FakeEmbeddingProvider` (fully implemented)
  - `OpenAiEmbeddingProvider` (skeleton/TODO)

### Incident explanation engine
- Interface: `LlmProvider`
- Implementations:
  - `NoopHeuristicExplainer` (fully implemented)
  - `OpenAiLlmProvider` (skeleton/TODO)

Selection is done in `ProviderConfiguration` using app properties (with optional env overrides).

---

## 11) Config Flags

From app config:

- `embedding.provider=fake|openai` (default `fake`)
- `llm.provider=heuristic|openai` (default `heuristic`)

Optional env overrides are also supported:
- `EMBEDDING_PROVIDER`
- `LLM_PROVIDER`

Default run mode is completely local and deterministic.

OpenAI classes currently throw `UnsupportedOperationException` until implemented.

---

## 12) Validation and Error Handling

Validation:
- Request DTOs use Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Valid`).

Global errors:
- `ApiExceptionHandler` handles:
  - validation errors (400)
  - malformed JSON (400)
  - bad arguments (400)
  - unexpected exceptions (500)

---

## 13) How To Run (Full Sequence)

1. Start DB
```bash
docker compose -f infra/docker-compose.yml up -d
```

2. Start API
```bash
gradle :apps:api:bootRun
```

3. Ingest KB
```bash
gradle :apps:api:ingestKnowledgeBase
```

4. Health check
```bash
curl http://localhost:8080/health
```

5. Retrieval test
```bash
curl "http://localhost:8080/kb/search?q=db%20pool%20timeout&k=5"
```

6. Incident explain test
```bash
curl -X POST "http://localhost:8080/incident/explain" \
  -H "Content-Type: application/json" \
  --data @data/incidents/incident-db-pool-exhaustion.json
```

---

## 14) Class Reading Order (If You Want To Learn The Code)

Read in this order:

1. `IncidentExplainerApiApplication`
2. `ProviderConfiguration`
3. `HealthController`
4. `KbIngestorApplication` + `KbIngestor`
5. `EmbeddingProvider` + `FakeEmbeddingProvider`
6. `KbSearchController` + `RetrievalService` + `KbSearchRepository`
7. `IncidentExplainController` + `IncidentExplainService`
8. `LlmProvider` + `NoopHeuristicExplainer`
9. `IncidentExplainRequest` + `IncidentExplainResponse`
10. `ApiExceptionHandler`

---

## 15) Beginner FAQ

### Why chunk text?
Because long documents are hard to retrieve precisely. Smaller overlapping chunks improve relevance.

### Why overlap chunks?
Important context can sit on boundaries; overlap keeps that context available in neighboring chunks.

### Why 384 dimensions?
It is the current chosen embedding size for MVP schema and fake embedding output.

### Is this AI?
Default mode is heuristic + vector retrieval, not external LLM generation.

### Can this be upgraded to real LLM/embeddings?
Yes. OpenAI provider skeletons exist specifically for that next step.
