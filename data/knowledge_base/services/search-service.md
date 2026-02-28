# Service Note: search-service

## Service Overview
Search-service powers full-text and semantic retrieval for user-facing query experiences and internal knowledge discovery.

## Critical Dependencies
- Postgres (metadata, ranking signals)
- Vector store index (pgvector embeddings)
- Redis cache (hot query/result caching)
- Ingestion pipeline (document freshness)

## SLOs
- Availability: 99.9%
- p95 latency: < 350ms
- Freshness: indexed content within 10 minutes for priority sources

## Known Failure Modes
1. **Embedding mismatch after model rollout**
   - Symptom: relevance drops, no hard errors
   - Detection: sudden CTR decline + semantic score drift
2. **DB saturation under high filter-cardinality queries**
   - Symptom: p95/p99 latency spikes, query timeouts
   - Detection: pg CPU > 85%, slow query logs
3. **Cache stampede on trending searches**
   - Symptom: bursty backend load, transient 5xx
   - Detection: cache miss ratio spikes + repeated key recomputation
4. **Stale index due to ingestion backlog**
   - Symptom: missing recent documents in results
   - Detection: ingestion lag and index freshness alerts

## Operational Notes
- Prefer canary rollout for ranking algorithm changes.
- Keep embedding dimension fixed per index (current MVP: 384).
- Warm top-100 query cache after deploys to reduce cold-start pressure.
