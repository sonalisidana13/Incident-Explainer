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
