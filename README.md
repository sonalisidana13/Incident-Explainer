# Incident Explainer Scaffold

Multi-module scaffold with:
- `apps/api`: Spring Boot 3 (Java 17), Gradle Kotlin DSL, Web + Validation + JDBC + Flyway
- `infra/docker-compose.yml`: Postgres with pgvector

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

Environment variables used by API/Flyway (defaults shown):
- `SPRING_DATASOURCE_URL` / `DB_URL`: `jdbc:postgresql://localhost:5432/incident_explainer`
- `SPRING_DATASOURCE_USERNAME` / `DB_USER`: `incident_explainer`
- `SPRING_DATASOURCE_PASSWORD` / `DB_PASSWORD`: `incident_explainer`

## Ingest Knowledge Base
The Java CLI `KbIngestor` reads all markdown files under `data/knowledge_base`, creates one
`documents` row per file, chunks text (~800-1200 chars, 200 overlap), generates deterministic
384-dim placeholder embeddings, and inserts into `chunks` with metadata.

Run ingestion:

```bash
gradle :apps:api:ingestKnowledgeBase
```

Optional input path override:

```bash
KB_ROOT=/absolute/path/to/data/knowledge_base gradle :apps:api:ingestKnowledgeBase
```

## Debug KB Retrieval
Vector search endpoint (pgvector cosine distance) using the same 384-dim deterministic embedding
function used by ingestion:

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
