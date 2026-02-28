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
