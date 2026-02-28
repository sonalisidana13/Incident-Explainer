# Runbook: Kafka Consumer Lag Growth

## Document Control
- Owner: Messaging Platform
- Last Reviewed: 2026-02-01
- Severity Mapping: Sev2 when lag threatens SLA; Sev1 when real-time workflows fail

## Trigger Conditions
- `consumer_lag > 100k` for 10 minutes on critical topics
- `consumer_lag_growth_rate > 10k/min` sustained 5 minutes
- Pager alert: `KAFKA-CONSUMER-LAG`

## Triage Steps
1. Identify affected topic, consumer group, and partitions.
2. Confirm whether lag is isolated to few partitions (skew) or all.
3. Check consumer health: rebalance loops, OOM, restarts.
4. Inspect producer rate changes and message size anomalies.

## Diagnostics
```bash
# Consumer group lag
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group notification-workers
```

```bash
# Pod health and restarts
kubectl get pods -n prod | grep notification-worker
kubectl logs -n prod deploy/notification-worker --tail=200
```

## Immediate Mitigations
- Scale consumer replicas if partition count permits.
- Pause non-critical producers to reduce ingress pressure.
- Increase max poll records / fetch size carefully.
- Reassign hot partitions if skew is severe.
- For poison messages, route offending records to DLQ policy.

## Escalation
- Engage messaging platform team for broker-side throttling issues.
- Engage service owner if consumer code errors or retry storms appear.

## Exit Criteria
- Lag trending down to normal operating range.
- No repeated consumer rebalance failures.
- End-to-end event processing latency within SLA.
