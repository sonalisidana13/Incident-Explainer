# Service Note: notification-service

## Service Overview
Notification-service processes events and sends email, push, and SMS notifications with channel-specific delivery policies.

## Critical Dependencies
- Kafka (event ingestion)
- Provider APIs (email/SMS/push gateways)
- Template service (message rendering)
- Postgres (delivery state and audit logs)

## SLOs
- Event-to-send p95: < 120 seconds
- Provider success rate: > 99.0% (excluding invalid recipients)
- Critical OTP delivery p95: < 30 seconds

## Known Failure Modes
1. **Consumer lag growth**
   - Symptom: delayed notifications and SLA breach
   - Detection: lag and lag growth rate alerts
2. **Poison message retry loop**
   - Symptom: throughput collapse, repeated processing failures
   - Detection: high retry counters for same event signatures
3. **Provider partial outage**
   - Symptom: channel-specific failures (e.g., SMS only)
   - Detection: per-provider error ratios and fallback activation
4. **Template rendering regression**
   - Symptom: malformed payloads rejected downstream
   - Detection: render failure metrics and DLQ volume increase

## Operational Notes
- Use DLQ for non-recoverable schema/validation failures.
- Keep idempotency keys for all outbound provider requests.
- During campaigns, pre-scale consumers and validate partition distribution.
