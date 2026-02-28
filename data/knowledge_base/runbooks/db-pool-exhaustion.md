# Runbook: Database Connection Pool Exhaustion

## Document Control
- Owner: Data Platform + Service Team
- Service Tier: Tier 1
- Last Reviewed: 2026-01-22

## Trigger Conditions
- Application metric: `db_pool_active == db_pool_max` for >2 minutes
- Error logs include:
  - `Timeout waiting for idle object`
  - `Connection is not available, request timed out`
- Pager alert: `DB-POOL-EXHAUSTION`

## Symptoms
- Request latency spikes before 5xx increase
- Thread pool backlog increases
- Short-lived bursts of 503/504 from API layer

## Triage Checklist
1. Identify services with exhausted pools.
2. Confirm DB server connection limits and current usage.
3. Check for long-lived transactions and blocked queries.
4. Review recent deploys affecting query patterns.

## Diagnostic Queries
```sql
-- Active sessions by application name
SELECT application_name, state, COUNT(*)
FROM pg_stat_activity
GROUP BY application_name, state
ORDER BY COUNT(*) DESC;
```

```sql
-- Long transactions (risking pool starvation)
SELECT pid, usename, now() - xact_start AS tx_age, query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY tx_age DESC
LIMIT 20;
```

## Mitigations
- Kill runaway long transactions after owner approval.
- Increase pool size cautiously if DB headroom exists.
- Reduce application concurrency (rate limits, worker count).
- Roll back releases introducing unbounded query fan-out.
- Route read-heavy traffic to read replica when possible.

## Prevention
- Enforce query timeout and transaction timeout defaults.
- Add dashboards for pool wait time and acquisition failures.
- Add static analysis/linting for missing pagination.

## Exit Criteria
- Pool saturation < 70% sustained for 20 minutes.
- Pool wait timeout errors return to baseline.
