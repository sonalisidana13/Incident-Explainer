# Postmortem: Checkout Latency Regression (2025-11-12)

## Summary
Checkout API p95 latency increased from 420ms to 2.8s for 47 minutes, causing abandonment uplift and revenue impact.

## Impact
- Start: 2025-11-12 14:03 UTC
- End: 2025-11-12 14:50 UTC
- Customer Impact: ~31% checkout attempts exceeded 3s
- Business Impact: Estimated 8.4% conversion drop during incident window

## Timeline
- 13:55 UTC: Release `checkout-service v2025.11.12-rc3` deployed to 100%
- 14:03 UTC: `LATENCY-P95-BREACH` alert triggered
- 14:08 UTC: On-call identified DB query time increase on `order_summary`
- 14:14 UTC: Incident bridge started (Sev2)
- 14:22 UTC: Feature flag `enable_dynamic_promotions` disabled
- 14:31 UTC: p95 reduced to 1.1s but still above SLO
- 14:38 UTC: Rollback to previous release initiated
- 14:50 UTC: Metrics normalized

## Root Cause
A new promotions enrichment path introduced an N+1 query pattern. Under peak load, DB CPU increased and query queueing amplified p95 latency.

## Mitigation
- Disabled `enable_dynamic_promotions` flag.
- Rolled back checkout-service to previous stable version.
- Added temporary autoscaling floor increase to absorb backlog.

## Action Items
1. Add query count guardrail tests in CI for checkout critical path. Owner: Checkout Team. Due: 2025-12-01.
2. Introduce p95 canary gate with automatic rollback policy. Owner: Platform. Due: 2025-12-10.
3. Add DB dashboard panel for per-endpoint query fan-out. Owner: Data Platform. Due: 2025-12-05.
4. Require performance sign-off for release notes touching promotion logic. Owner: Eng Mgmt. Due: 2025-11-25.
