# Postmortem: Auth-Service 5xx Spike (2025-12-03)

## Summary
Auth-service returned elevated 5xx due to DB pool exhaustion after a retry policy misconfiguration increased concurrent DB attempts.

## Impact
- Start: 2025-12-03 09:18 UTC
- End: 2025-12-03 10:02 UTC
- Customer Impact: Login failures peaked at 12.6%
- Internal Impact: Elevated support ticket volume and SSO timeout escalations

## Timeline
- 09:12 UTC: Config rollout enabling aggressive retry (`maxRetries=8`) completed
- 09:18 UTC: `API-EDGE-5XX-SPIKE` fired for `/auth/login`
- 09:24 UTC: On-call observed DB pool active=100%, wait timeouts rising
- 09:29 UTC: Incident declared Sev1 due to login path impact
- 09:36 UTC: Retry config reverted (`maxRetries=2`)
- 09:44 UTC: Connection pool stabilized, error rate down to 2.1%
- 10:02 UTC: Incident resolved

## Root Cause
Retry amplification multiplied in-flight DB calls during transient latency, exhausting the auth-service connection pool and cascading into request failures.

## Mitigation
- Reverted retry policy to prior safe values.
- Added temporary login rate shaping for abusive client patterns.
- Restarted a subset of saturated pods to clear queue backlog.

## Action Items
1. Add config policy validation to block retry values above service guardrail. Owner: Auth Team. Due: 2025-12-12.
2. Implement jittered exponential backoff with global retry budget. Owner: Platform Runtime. Due: 2025-12-20.
3. Add alert on pool wait time before hard exhaustion. Owner: SRE. Due: 2025-12-09.
