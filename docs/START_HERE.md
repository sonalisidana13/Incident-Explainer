# Incident Explainer: Start Here

This is the easiest path to run the project locally.

## What This App Does
- Stores a knowledge base (runbooks, postmortems, service notes) in Postgres + pgvector.
- Retrieves relevant KB chunks for incidents.
- Generates deterministic incident explanations (no external LLM in default mode).

## Tech Stack
- API: Spring Boot (Java 17)
- DB: Postgres + pgvector (Docker)
- Ingestion: Java CLI (`KbIngestor`)

## Prerequisites
- Docker + Docker Compose
- Java 17
- Gradle (CLI available as `gradle`)

## 1) Start Database
From repo root:

```bash
docker compose -f infra/docker-compose.yml up -d
```

This starts Postgres with pgvector enabled.

## 2) Start API
In a new terminal:

```bash
gradle :apps:api:bootRun
```

Defaults are already set in app config:
- `embedding.provider=fake`
- `llm.provider=heuristic`

Health check:

```bash
curl http://localhost:8080/health
```

Expected:

```json
{"status":"ok","service":"incident-explainer-api"}
```

## 3) Ingest Knowledge Base
Run once after DB + API config are ready:

```bash
gradle :apps:api:ingestKnowledgeBase
```

What it does:
- Reads `data/knowledge_base/**/*.md`
- Inserts one `documents` row per file
- Splits text into overlapping chunks
- Generates 384-dim embeddings
- Inserts rows into `chunks`

## 4) Test Retrieval
```bash
curl "http://localhost:8080/kb/search?q=db%20pool%20timeouts&k=5"
```

## 5) Test Incident Explanation
Single sample:

```bash
curl -X POST "http://localhost:8080/incident/explain" \
  -H "Content-Type: application/json" \
  --data @data/incidents/incident-db-pool-exhaustion.json
```

All sample packs:

```bash
for f in data/incidents/*.json; do
  echo "==> $f"
  curl -s -X POST "http://localhost:8080/incident/explain" \
    -H "Content-Type: application/json" \
    --data @"$f"
  echo
 done
```

## Provider Switching
Edit either config file:
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/application.properties`

Set:
- `embedding.provider=fake|openai`
- `llm.provider=heuristic|openai`

Current local defaults:
- `embedding.provider=fake`
- `llm.provider=heuristic`

You can still override with env vars later (`EMBEDDING_PROVIDER`, `LLM_PROVIDER`) if needed.

## How Runtime Flow Works
1. `KbIngestor` loads and embeds KB markdown into Postgres.
2. `/incident/explain` builds a retrieval query from alert/logs/metrics.
3. Retrieval service performs pgvector similarity search.
4. Heuristic explainer maps evidence to likely causes + first checks + citations.
5. API returns strict JSON response.

## Common Issues
- `gradle: command not found`:
  install Gradle or add wrapper (`gradlew`) to the repo.
- DB connection errors:
  ensure Docker DB is running and `5432` is available.
- Empty retrieval results:
  run ingestion and verify KB files exist under `data/knowledge_base`.
