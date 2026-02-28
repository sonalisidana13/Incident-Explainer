package com.incidentexplainer.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NoopHeuristicExplainer implements LlmProvider {

    private static final int MAX_CAUSES = 3;
    private static final int MAX_CHECKS = 4;
    private static final int SNIPPET_LIMIT = 220;
    private static final double MIN_CAUSE_CONFIDENCE = 0.35;
    private static final double RETRIEVAL_WEAK_THRESHOLD = 0.25;

    private static final List<CauseRule> CAUSE_RULES = List.of(
        new CauseRule(
            CauseType.DB_POOL,
            "Database pool exhaustion",
            List.of("db pool", "connection pool", "hikari", "too many clients", "timeout waiting for idle", "connection is not available"),
            "Signals align with database connection starvation and pool wait timeouts.",
            "Check DB pool utilization and long-running transactions.",
            "Pool saturation can quickly cascade into request timeouts and 5xx responses."
        ),
        new CauseRule(
            CauseType.TIMEOUT,
            "Downstream timeout or latency regression",
            List.of("timeout", "timed out", "latency", "p95", "p99", "slow query", "deadline exceeded"),
            "Evidence indicates elevated tail latency and timeout pressure in the request path.",
            "Inspect p95/p99 latency by endpoint and dependency call timings.",
            "Timeouts usually indicate downstream slowness, queueing, or saturation."
        ),
        new CauseRule(
            CauseType.OOM,
            "Memory pressure causing OOM/restarts",
            List.of("oom", "outofmemory", "out of memory", "killed", "crashloop", "restart"),
            "Pattern matches memory exhaustion and restart-loop behavior.",
            "Review OOMKilled events, restart counts, and memory headroom.",
            "OOM-driven restarts cause intermittent failures and unstable latency."
        ),
        new CauseRule(
            CauseType.KAFKA_LAG,
            "Kafka consumer lag or poison-message loop",
            List.of("consumer lag", "kafka", "offset", "rebalance", "dlq", "poison"),
            "Evidence points to event-processing delay and consumer throughput imbalance.",
            "Check lag by partition, rebalance events, and DLQ growth.",
            "Lag spikes often come from partition skew, retries, or malformed messages."
        ),
        new CauseRule(
            CauseType.HTTP_5XX,
            "HTTP 5xx spike from dependency or service instability",
            List.of("5xx", "500", "502", "503", "504", "internal server error"),
            "Observed indicators match server error-rate escalation.",
            "Break down 5xx by endpoint, region, and recent change window.",
            "Fast blast-radius identification narrows whether this is local, regional, or dependency-wide."
        ),
        new CauseRule(
            CauseType.DEPLOYMENT,
            "Recent deployment regression",
            List.of("deploy", "release", "rollback", "version"),
            "Recent change activity overlaps the alert window and may have introduced regression risk.",
            "Compare incident start time with latest deploys and rollback readiness.",
            "Change correlation is one of the fastest ways to confirm or eliminate regression causes."
        )
    );

    @Override
    public IncidentExplainResponse explain(IncidentExplainRequest request, List<RetrievedChunk> retrievedChunks) {
        IncidentExplainRequest.Alert alert = request.alert();
        List<String> logs = request.logs() == null ? List.of() : request.logs();
        Map<String, String> metrics = request.metrics() == null ? Map.of() : request.metrics();
        List<IncidentExplainRequest.DeployEvent> deploys = request.deploys() == null ? List.of() : request.deploys();

        boolean logsMissing = logs.isEmpty();
        boolean retrievalWeak = isRetrievalWeak(retrievedChunks);
        boolean insufficientEvidence = logsMissing && retrievalWeak;

        List<ScoredCause> scoredCauses = scoreCauses(alert, logs, metrics, deploys, retrievedChunks);
        List<IncidentExplainResponse.LikelyCause> likelyCauses = scoredCauses.stream()
            .filter(cause -> !cause.citations().isEmpty())
            .sorted(Comparator.comparingDouble(ScoredCause::confidence).reversed())
            .limit(MAX_CAUSES)
            .map(cause -> new IncidentExplainResponse.LikelyCause(
                cause.title(),
                round(cause.confidence()),
                cause.reason(),
                cause.citations()
            ))
            .toList();

        List<IncidentExplainResponse.FirstCheck> firstChecks = buildFirstChecks(likelyCauses, scoredCauses);
        List<String> nextDataToFetch = buildNextDataToFetch(alert, logsMissing, retrievalWeak, metrics, deploys);
        String summary = buildSummary(alert, likelyCauses, insufficientEvidence);

        IncidentExplainResponse.DebugInfo debugInfo = new IncidentExplainResponse.DebugInfo(
            retrievedChunks.stream()
                .map(chunk -> new IncidentExplainResponse.RetrievedChunkDebug(
                    chunk.chunkId(),
                    chunk.documentSource(),
                    chunk.documentTitle(),
                    round(chunk.score())
                ))
                .toList()
        );

        return new IncidentExplainResponse(
            summary,
            likelyCauses,
            firstChecks,
            nextDataToFetch,
            debugInfo
        );
    }

    private List<ScoredCause> scoreCauses(
        IncidentExplainRequest.Alert alert,
        List<String> logs,
        Map<String, String> metrics,
        List<IncidentExplainRequest.DeployEvent> deploys,
        List<RetrievedChunk> retrieved
    ) {
        String alertText = (alert.name() + " " + alert.description() + " " + alert.service()).toLowerCase(Locale.ROOT);
        String metricText = metricsToText(metrics);

        List<ScoredCause> out = new ArrayList<>();
        for (CauseRule rule : CAUSE_RULES) {
            int alertHits = countHits(alertText, rule.keywords());
            int metricHits = countHits(metricText, rule.keywords());
            int logHits = countHits(String.join(" ", logs).toLowerCase(Locale.ROOT), rule.keywords());
            int kbHits = countChunkHits(retrieved, rule.keywords());

            boolean deploySignal = rule.type() == CauseType.DEPLOYMENT && !deploys.isEmpty();

            double confidence = 0.10;
            confidence += Math.min(0.25, alertHits * 0.12);
            confidence += Math.min(0.15, metricHits * 0.08);
            confidence += Math.min(0.25, logHits * 0.08);
            confidence += Math.min(0.25, kbHits * 0.10);
            if (deploySignal) {
                confidence += 0.20;
            }
            confidence = clamp(confidence, 0.0, 0.95);

            List<IncidentExplainResponse.Citation> citations = buildCitations(rule.keywords(), logs, retrieved);
            if (citations.isEmpty()) {
                continue;
            }

            if (confidence < MIN_CAUSE_CONFIDENCE && !deploySignal) {
                continue;
            }

            String reason = buildReason(rule, alert, alertHits, metricHits, logHits, kbHits, deploySignal);
            out.add(new ScoredCause(rule.type(), rule.title(), confidence, reason, citations));
        }

        return out;
    }

    private List<IncidentExplainResponse.Citation> buildCitations(
        List<String> keywords,
        List<String> logs,
        List<RetrievedChunk> retrieved
    ) {
        List<IncidentExplainResponse.Citation> citations = new ArrayList<>();

        IncidentExplainResponse.Citation kbCitation = findKbCitation(keywords, retrieved);
        if (kbCitation != null) {
            citations.add(kbCitation);
        }

        IncidentExplainResponse.Citation logCitation = findLogCitation(keywords, logs);
        if (logCitation != null) {
            citations.add(logCitation);
        }

        if (citations.isEmpty() && !retrieved.isEmpty()) {
            RetrievedChunk chunk = retrieved.get(0);
            citations.add(new IncidentExplainResponse.Citation(
                "kb",
                chunk.documentSource(),
                chunk.chunkId(),
                snippet(chunk.text(), keywords)
            ));
        }

        return citations;
    }

    private IncidentExplainResponse.Citation findKbCitation(List<String> keywords, List<RetrievedChunk> retrieved) {
        for (RetrievedChunk chunk : retrieved) {
            String hay = (chunk.documentTitle() + " " + chunk.text()).toLowerCase(Locale.ROOT);
            if (containsAny(hay, keywords)) {
                return new IncidentExplainResponse.Citation(
                    "kb",
                    chunk.documentSource(),
                    chunk.chunkId(),
                    snippet(chunk.text(), keywords)
                );
            }
        }
        return null;
    }

    private IncidentExplainResponse.Citation findLogCitation(List<String> keywords, List<String> logs) {
        for (int i = 0; i < logs.size(); i++) {
            String line = logs.get(i);
            if (containsAny(line.toLowerCase(Locale.ROOT), keywords)) {
                return new IncidentExplainResponse.Citation(
                    "logs",
                    "logs[" + i + "]",
                    -1,
                    snippet(line, keywords)
                );
            }
        }
        return null;
    }

    private List<IncidentExplainResponse.FirstCheck> buildFirstChecks(
        List<IncidentExplainResponse.LikelyCause> likelyCauses,
        List<ScoredCause> scoredCauses
    ) {
        List<IncidentExplainResponse.FirstCheck> checks = new ArrayList<>();
        for (IncidentExplainResponse.LikelyCause cause : likelyCauses) {
            CauseRule rule = ruleForTitle(cause.title());
            if (rule == null) {
                continue;
            }

            List<IncidentExplainResponse.Citation> citations = cause.citations().isEmpty()
                ? List.of()
                : List.of(cause.citations().get(0));

            if (citations.isEmpty()) {
                continue;
            }

            checks.add(new IncidentExplainResponse.FirstCheck(
                rule.step(),
                rule.why(),
                citations
            ));

            if (checks.size() >= MAX_CHECKS) {
                break;
            }
        }

        if (checks.isEmpty() && !scoredCauses.isEmpty()) {
            ScoredCause fallback = scoredCauses.get(0);
            checks.add(new IncidentExplainResponse.FirstCheck(
                "Validate the top evidence source and narrow blast radius.",
                "A focused verification step reduces time to first concrete hypothesis.",
                List.of(fallback.citations().get(0))
            ));
        }

        return checks;
    }

    private CauseRule ruleForTitle(String title) {
        for (CauseRule rule : CAUSE_RULES) {
            if (rule.title().equals(title)) {
                return rule;
            }
        }
        return null;
    }

    private List<String> buildNextDataToFetch(
        IncidentExplainRequest.Alert alert,
        boolean logsMissing,
        boolean retrievalWeak,
        Map<String, String> metrics,
        List<IncidentExplainRequest.DeployEvent> deploys
    ) {
        Set<String> next = new LinkedHashSet<>();

        if (logsMissing) {
            next.add("Collect error logs and stack traces for " + alert.service() + " between " + alert.startTime() + " and " + alert.endTime() + ".");
        }

        if (metrics.isEmpty()) {
            next.add("Fetch service metrics: error rate, p95/p99 latency, CPU, memory, and DB pool usage for the alert window.");
        }

        if (deploys.isEmpty()) {
            next.add("Fetch deploy/change events (version, timestamp, rollback status) around incident start time.");
        }

        if (retrievalWeak) {
            next.add("Fetch service-specific runbook/postmortem material for " + alert.service() + " and verify KB coverage.");
        }

        return new ArrayList<>(next);
    }

    private String buildSummary(
        IncidentExplainRequest.Alert alert,
        List<IncidentExplainResponse.LikelyCause> likelyCauses,
        boolean insufficientEvidence
    ) {
        if (insufficientEvidence) {
            return "insufficient evidence: logs are missing and retrieved knowledge-base evidence is weak; collect additional runtime data before concluding root cause.";
        }

        if (likelyCauses.isEmpty()) {
            return "Alert " + alert.name() + " for service " + alert.service() + " in " + alert.env()
                + " is active, but no high-confidence cause was derived from current evidence.";
        }

        IncidentExplainResponse.LikelyCause top = likelyCauses.get(0);
        return "Alert " + alert.name() + " for service " + alert.service() + " in " + alert.env()
            + " is most consistent with " + top.title().toLowerCase(Locale.ROOT)
            + " (confidence " + round(top.confidence()) + ").";
    }

    private boolean isRetrievalWeak(List<RetrievedChunk> retrievedChunks) {
        if (retrievedChunks.isEmpty()) {
            return true;
        }

        int limit = Math.min(3, retrievedChunks.size());
        double total = 0.0;
        for (int i = 0; i < limit; i++) {
            total += retrievedChunks.get(i).score();
        }
        double average = total / limit;
        return average < RETRIEVAL_WEAK_THRESHOLD;
    }

    private int countChunkHits(List<RetrievedChunk> chunks, List<String> keywords) {
        int hits = 0;
        for (RetrievedChunk chunk : chunks) {
            String text = (chunk.documentTitle() + " " + chunk.text()).toLowerCase(Locale.ROOT);
            if (containsAny(text, keywords)) {
                hits++;
            }
        }
        return hits;
    }

    private int countHits(String text, List<String> keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String metricsToText(Map<String, String> metrics) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : metrics.entrySet()) {
            sb.append(entry.getKey()).append(' ').append(entry.getValue()).append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String buildReason(
        CauseRule rule,
        IncidentExplainRequest.Alert alert,
        int alertHits,
        int metricHits,
        int logHits,
        int kbHits,
        boolean deploySignal
    ) {
        StringBuilder sb = new StringBuilder(rule.reason());
        sb.append(" Alert/service keyword hits=").append(alertHits);
        sb.append(", metric hits=").append(metricHits);
        sb.append(", log hits=").append(logHits);
        sb.append(", kb hits=").append(kbHits).append('.');
        if (deploySignal) {
            sb.append(" Recent deploy activity is present and overlaps incident context.");
        }
        sb.append(" Service=").append(alert.service()).append('.');
        return sb.toString();
    }

    private String snippet(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = -1;
        for (String keyword : keywords) {
            int position = lower.indexOf(keyword);
            if (position >= 0) {
                idx = position;
                break;
            }
        }

        if (idx < 0) {
            return trim(text, SNIPPET_LIMIT);
        }

        int start = Math.max(0, idx - 80);
        int end = Math.min(text.length(), idx + 140);
        return trim(text.substring(start, end), SNIPPET_LIMIT);
    }

    private String trim(String text, int max) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max - 3) + "...";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record CauseRule(
        CauseType type,
        String title,
        List<String> keywords,
        String reason,
        String step,
        String why
    ) {}

    private record ScoredCause(
        CauseType type,
        String title,
        double confidence,
        String reason,
        List<IncidentExplainResponse.Citation> citations
    ) {}

    private enum CauseType {
        DB_POOL,
        TIMEOUT,
        OOM,
        KAFKA_LAG,
        HTTP_5XX,
        DEPLOYMENT
    }
}
