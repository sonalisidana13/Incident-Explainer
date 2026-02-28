# Postmortem: Notification Pipeline Lag (2026-01-19)

## Summary
Kafka consumer lag in notification-service exceeded SLA after uneven partition key distribution and a poison message retry loop.

## Impact
- Start: 2026-01-19 02:07 UTC
- End: 2026-01-19 03:21 UTC
- Customer Impact: Delayed email/push delivery, median delay 18 minutes
- Business Impact: Time-sensitive OTP notifications breached delivery objective

## Timeline
- 01:58 UTC: Traffic campaign started (3.2x normal event rate)
- 02:07 UTC: `KAFKA-CONSUMER-LAG` alert triggered
- 02:15 UTC: Hot partition and retry storm identified
- 02:24 UTC: DLQ rule enabled for malformed payload signature
- 02:38 UTC: Consumer replicas scaled from 8 to 20
- 02:52 UTC: Lag growth stopped; backlog started draining
- 03:21 UTC: Lag returned to baseline

## Root Cause
Producer keying logic used low-cardinality key for a campaign path, creating partition hot-spotting. A malformed message type repeatedly retried without fast-fail to DLQ, reducing effective throughput.

## Mitigation
- Enabled DLQ for malformed event class.
- Scaled consumer group and increased fetch batch sizes.
- Patched producer partition key strategy to include tenant hash.

## Action Items
1. Enforce partition-key cardinality checks in producer CI. Owner: Messaging Platform. Due: 2026-02-05.
2. Add poison-message detector with automatic DLQ fallback after threshold. Owner: Notification Team. Due: 2026-02-08.
3. Add campaign pre-flight load simulation for notification pipeline. Owner: Growth Eng. Due: 2026-02-12.
