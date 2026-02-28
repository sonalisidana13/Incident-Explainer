# Runbook: HTTP 5xx Error Rate Spike

## Document Control
- Owner: SRE On-Call
- Service Tier: Tier 1
- Last Reviewed: 2026-01-15
- Severity Mapping: Sev2 if sustained >10 min, Sev1 if customer-critical path unavailable

## Purpose
Provide immediate triage and mitigation steps when 5xx errors increase above normal baseline.

## Trigger Conditions
- `http_5xx_rate > 2%` for 5 minutes across all regions
- `http_5xx_rate > 5%` for 2 minutes in a single region
- Pager alert: `API-EDGE-5XX-SPIKE`

## Primary Signals
- Load balancer 5xx metrics by backend target
- Application logs (`status>=500`)
- Deployment events in the last 60 minutes
- DB and cache dependency health

## Initial Triage (First 10 Minutes)
1. Confirm blast radius by endpoint, region, tenant segment.
2. Check if error pattern is concentrated:
   - single route
   - single pod/node
   - single AZ/region
3. Correlate with recent deploy/config changes.
4. Identify top exception types and counts from logs.

## Diagnostic Queries
```sql
-- Top failing endpoints in last 10 minutes
SELECT route, COUNT(*) AS errors
FROM request_logs
WHERE status_code >= 500
  AND ts > NOW() - INTERVAL '10 minutes'
GROUP BY route
ORDER BY errors DESC
LIMIT 20;
```

```bash
# Kubernetes: restart loops and readiness failures
kubectl get pods -n prod -o wide | egrep 'CrashLoopBackOff|Error|0/1|0/2'
kubectl describe deploy api-gateway -n prod
```

## Fast Mitigations
- Roll back last deployment if failure aligns with release window.
- Shift traffic away from unhealthy region/AZ.
- Enable circuit breaker for failing downstream dependency.
- Apply temporary rate limits for abusive or high-cost endpoints.
- Increase replica count if saturation-driven (CPU, queue depth).

## Decision Matrix
- If rollback restores health within 5 minutes: classify as release regression.
- If dependency outage confirmed: initiate cross-team incident bridge.
- If only one endpoint fails: isolate route via feature flag or targeted disable.

## Escalation
- Page service owner if not stabilized within 15 minutes.
- Escalate to incident commander if customer checkout/login impacted.
- Notify support with customer-facing summary every 30 minutes.

## Exit Criteria
- 5xx rate below 1% for 20 continuous minutes.
- No active crash loops.
- Error budget burn rate returns to baseline.

## Post-Incident Artifacts
- Incident timeline
- Root cause hypothesis and validation
- Corrective and preventive action items
