# Service Note: auth-service

## Service Overview
Auth-service manages login, token issuance/validation, session revocation, and policy checks for protected APIs.

## Critical Dependencies
- Postgres (user/session metadata)
- Redis (session cache, token blacklist)
- Identity provider integrations (SAML/OIDC)
- Key management service (JWT signing keys)

## SLOs
- Availability: 99.95%
- p95 login latency: < 250ms
- Token validation latency: < 50ms (p95)

## Known Failure Modes
1. **DB pool exhaustion**
   - Symptom: login 5xx spike and timeout errors
   - Detection: pool wait time and active=max metrics
2. **JWT signing key rotation drift**
   - Symptom: token validation failures across services
   - Detection: signature verification error increase
3. **IdP callback latency surge**
   - Symptom: elevated login latency without internal DB saturation
   - Detection: upstream dependency latency dashboard
4. **Cache inconsistency for revoked sessions**
   - Symptom: stale sessions remain valid longer than policy
   - Detection: audit mismatch between DB and cache checks

## Operational Notes
- Always roll out auth config changes with staged percentages.
- Retry budgets must be capped to avoid load amplification.
- Maintain break-glass admin access procedures with quarterly validation.
