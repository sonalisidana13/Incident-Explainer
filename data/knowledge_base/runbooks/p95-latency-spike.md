# Runbook: p95 Latency Spike

## Document Control
- Owner: Performance Engineering
- Service Tier: Tier 1
- Last Reviewed: 2026-01-10
- Severity Mapping: Sev2 if p95 > 2x SLO for 15 min; Sev1 if user-critical latency > 5x

## Purpose
Guide responders through diagnosis and mitigation of elevated tail latency.

## Trigger Conditions
- `p95_latency_ms > 2 * SLO` for 10 minutes
- `p99_latency_ms > 4 * SLO` for 5 minutes
- Pager alert: `LATENCY-P95-BREACH`

## Common Root Causes
- Downstream dependency slowness (DB/cache/external API)
- Thread pool or connection pool saturation
- Large payload amplification or N+1 behavior
- GC pressure and CPU throttling

## Initial Triage
1. Segment latency by endpoint and dependency call chain.
2. Compare p50 vs p95/p99 to identify tail-only issue.
3. Check resource saturation: CPU, memory, thread pools, DB pools.
4. Validate whether request volume changed (spike vs same load).

## Diagnostic Steps
```bash
# Check pod CPU throttling and restarts
kubectl top pods -n prod
kubectl get pods -n prod --sort-by=.status.containerStatuses[0].restartCount
```

```sql
-- Long-running DB queries
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 20;
```

## Fast Mitigations
- Enable cached responses for high-read endpoints.
- Temporarily raise pool sizes only if backend capacity allows.
- Disable expensive optional features via feature flags.
- Scale out stateless services to reduce queueing delay.
- Apply request timeout/circuit breaker to slow dependencies.

## Escalation Guidance
- Involve DBA when query wait times exceed baseline by >3x.
- Involve platform team when node-level saturation appears.
- Escalate to incident commander if SLO breach impacts top customer flows.

## Exit Criteria
- p95 and p99 below SLO thresholds for 30 minutes.
- Queue depth and wait metrics normalized.
- No elevated timeout/error spillover.
