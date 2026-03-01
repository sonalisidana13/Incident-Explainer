# Incident Explainer Scaffold

Multi-module scaffold with:
- `apps/api`: Spring Boot 3 (Java 17), Gradle Kotlin DSL, Web + Validation + JDBC + Flyway
- `infra/docker-compose.yml`: Postgres with pgvector

Easy guide: `docs/START_HERE.md`
Full beginner architecture guide: `docs/PROJECT_GUIDE.md`

## Start Postgres

```bash
docker compose -f infra/docker-compose.yml up -d
```

## Run API

```bash
gradle :apps:api:bootRun
```

API health endpoint:

```bash
curl http://localhost:8080/health
```

## Run Migrations
Flyway migrations are auto-run on API startup. To run explicitly:

```bash
gradle :apps:api:flywayMigrate
```

Database config is already set in:
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/application.properties`

Current local defaults:
- URL: `jdbc:postgresql://localhost:5432/incident_explainer`
- Username: `incident_explainer`
- Password: `incident_explainer`

## Ingest Knowledge Base
The Java CLI `KbIngestor` reads all markdown files under `data/knowledge_base`, creates one
`documents` row per file, chunks text (~800-1200 chars, 200 overlap), generates 384-dim
embeddings via the configured embedding provider, and inserts into `chunks` with metadata.

Run ingestion:

```bash
gradle :apps:api:ingestKnowledgeBase
```

Optional input path override:

```bash
KB_ROOT=/absolute/path/to/data/knowledge_base gradle :apps:api:ingestKnowledgeBase
```

## Debug KB Retrieval
Vector search endpoint (pgvector cosine distance) using the same configured embedding provider
used by ingestion:

```bash
curl "http://localhost:8080/kb/search?q=database%20pool%20timeouts&k=5"
```

Optional filters:

```bash
curl "http://localhost:8080/kb/search?q=consumer%20lag&k=5&kind=runbooks&service=notification-service"
```

## Incident Explain Endpoint
Deterministic MVP endpoint (no external LLM) using alert keywords, optional logs/metrics/deploys,
and KB retrieval heuristics.

```bash
curl -X POST "http://localhost:8080/incident/explain" \
  -H "Content-Type: application/json" \
  -d '{
    "alert": {
      "name": "High 5xx rate",
      "service": "auth-service",
      "env": "prod",
      "startTime": "2026-02-28T02:10:00Z",
      "endTime": "2026-02-28T02:20:00Z",
      "description": "Login API 5xx and timeout errors are elevated"
    },
    "logs": [
      "Timeout waiting for idle object in db pool",
      "POST /auth/login returned 503"
    ],
    "metrics": {
      "p95_latency_ms": "2100",
      "error_rate": "0.12"
    },
    "deploys": [
      { "version": "auth-service-1.18.4", "time": "2026-02-28T02:05:00Z" }
    ]
  }'
```

### Post Sample Incident Packs
Sample request payloads are available under `data/incidents`.

Post one sample:

```bash
curl -X POST "http://localhost:8080/incident/explain" \
  -H "Content-Type: application/json" \
  --data @data/incidents/incident-db-pool-exhaustion.json
```

Post all samples:

```bash
for f in data/incidents/*.json; do
  echo "==> $f"
  curl -s -X POST "http://localhost:8080/incident/explain" \
    -H "Content-Type: application/json" \
    --data @"$f"
  echo
done
```

## Provider Configuration
Provider abstraction is configured in app properties:
- `embedding.provider=fake|openai`
- `llm.provider=heuristic|openai`

Current local defaults (already set):
- `embedding.provider=fake`
- `llm.provider=heuristic`

To switch providers, edit either:
- `apps/api/src/main/resources/application.yml`, or
- `apps/api/src/main/resources/application.properties`

You can still override with env vars if needed later:
- `EMBEDDING_PROVIDER`
- `LLM_PROVIDER`

Notes:
- `FakeEmbeddingProvider` is fully implemented and stable for local MVP use.
- `NoopHeuristicExplainer` is fully implemented and does not call external LLMs.
- `OpenAiEmbeddingProvider` and `OpenAiLlmProvider` are class skeletons with TODOs and method signatures only; they currently throw `UnsupportedOperationException` until implemented.
