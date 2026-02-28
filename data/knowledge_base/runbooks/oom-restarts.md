# Runbook: OOM Kills / Restart Storm

## Document Control
- Owner: Runtime Platform
- Last Reviewed: 2026-01-28
- Severity Mapping: Sev2 if >10% pods restarting, Sev1 for critical service outage

## Trigger Conditions
- `container_oom_events > 0`
- `restart_count` increasing continuously
- Pager alert: `OOM-RESTART-STORM`

## Symptoms
- Elevated latency and intermittent 5xx
- Readiness probe failures
- Rapid oscillation in replica availability

## Triage
1. Confirm OOMKilled reason from pod events.
2. Compare memory working set vs container limits.
3. Identify recent changes in traffic shape, payload size, or release.
4. Inspect heap/native memory usage and GC behavior.

## Diagnostics
```bash
kubectl describe pod <pod-name> -n prod | grep -A5 -i "State\|Reason\|OOM"
kubectl top pod <pod-name> -n prod --containers
```

```text
Look for log signatures:
- java.lang.OutOfMemoryError
- FATAL: JavaScript heap out of memory
- allocator OOM / cgroup kill
```

## Fast Mitigations
- Increase memory limits temporarily with matching requests.
- Reduce concurrency / batch size to lower per-process footprint.
- Disable memory-intensive features/flags.
- Roll back recent release introducing leak/regression.

## Recovery and Hardening
- Capture heap/profile artifacts for offline analysis.
- Add canary memory guardrails before global rollout.
- Tune GC and heap settings by runtime.
- Add alert on memory growth slope, not only absolute threshold.

## Exit Criteria
- Restart rate returns to baseline.
- No OOM events for 30 minutes.
- Memory usage stable with acceptable headroom.
