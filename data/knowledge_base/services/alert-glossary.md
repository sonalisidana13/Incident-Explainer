# Alert Glossary

## Purpose
Standard definitions for common alert terms used across SRE, platform, and application teams.

## Terms
- **5xx Error Rate**: Percentage of requests returning HTTP status 500-599.
- **p95 Latency**: 95th percentile response time for a request population.
- **Error Budget Burn Rate**: Rate at which SLO error budget is consumed relative to target period.
- **Connection Pool Exhaustion**: State where no DB connections are available within timeout.
- **Consumer Lag**: Difference between latest Kafka offset and consumer committed offset.
- **OOM Kill**: Container/process terminated by kernel cgroup memory enforcement.
- **Retry Storm**: Excessive retries amplifying traffic/load and reducing recovery ability.
- **Blast Radius**: Scope of impact across customers, services, and regions.
- **Mitigation**: Action that reduces impact quickly, not necessarily root-cause fix.
- **Remediation**: Corrective change eliminating underlying root cause.

## Severity Guidelines (Default)
- **Sev1**: Critical user journey unavailable or severe safety/security impact.
- **Sev2**: Significant degradation in key experience with workaround.
- **Sev3**: Limited or partial degradation with minor customer impact.

## Communication Expectations
- Update incident channel every 15 minutes for Sev1, every 30 minutes for Sev2.
- Include: current impact, mitigation in progress, ETA confidence level.
