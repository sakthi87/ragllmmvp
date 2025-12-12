package com.yugabyte.rag.service;

import com.yugabyte.rag.controller.MonitoringController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Metrics Service for tracking and aggregating RAG system metrics
 * Tracks: query analytics, performance metrics, intent detection accuracy
 */
@Service
@Slf4j
public class MetricsService {
    
    // In-memory metrics storage (in production, use a proper metrics DB)
    private final Map<String, QueryMetric> queryMetrics = new ConcurrentHashMap<>();
    private final Map<String, PerformanceMetric> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, IntentMetric> intentMetrics = new ConcurrentHashMap<>();
    
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong successfulQueries = new AtomicLong(0);
    private final AtomicLong failedQueries = new AtomicLong(0);
    
    /**
     * Record a query metric
     */
    public void recordQuery(String requestId, String question, List<String> intents, 
                           long latencyMs, boolean success) {
        QueryMetric metric = new QueryMetric(
            requestId,
            question,
            intents,
            latencyMs,
            success,
            LocalDateTime.now()
        );
        
        queryMetrics.put(requestId, metric);
        
        totalQueries.incrementAndGet();
        if (success) {
            successfulQueries.incrementAndGet();
        } else {
            failedQueries.incrementAndGet();
        }
        
        // Keep only last 1000 metrics (simple cleanup)
        if (queryMetrics.size() > 1000) {
            String oldestKey = queryMetrics.keySet().iterator().next();
            queryMetrics.remove(oldestKey);
        }
    }
    
    /**
     * Record a performance metric
     */
    public void recordPerformance(String requestId, Map<String, Long> stepLatencies) {
        PerformanceMetric metric = new PerformanceMetric(
            requestId,
            stepLatencies,
            LocalDateTime.now()
        );
        
        performanceMetrics.put(requestId, metric);
        
        // Keep only last 1000 metrics
        if (performanceMetrics.size() > 1000) {
            String oldestKey = performanceMetrics.keySet().iterator().next();
            performanceMetrics.remove(oldestKey);
        }
    }
    
    /**
     * Record an intent detection metric
     */
    public void recordIntent(String requestId, List<String> detectedIntents, boolean accurate) {
        IntentMetric metric = new IntentMetric(
            requestId,
            detectedIntents,
            accurate,
            LocalDateTime.now()
        );
        
        intentMetrics.put(requestId, metric);
        
        // Keep only last 1000 metrics
        if (intentMetrics.size() > 1000) {
            String oldestKey = intentMetrics.keySet().iterator().next();
            intentMetrics.remove(oldestKey);
        }
    }
    
    /**
     * Get query analytics for the last N hours
     */
    public MonitoringController.QueryAnalytics getQueryAnalytics(int lookbackHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(lookbackHours);
        
        List<QueryMetric> recentMetrics = queryMetrics.values().stream()
            .filter(m -> m.getTimestamp().isAfter(cutoff))
            .collect(Collectors.toList());
        
        if (recentMetrics.isEmpty()) {
            return createEmptyQueryAnalytics();
        }
        
        MonitoringController.QueryAnalytics analytics = new MonitoringController.QueryAnalytics();
        
        // Total queries
        analytics.setTotalQueries(recentMetrics.size());
        
        // Latency statistics
        List<Long> latencies = recentMetrics.stream()
            .map(QueryMetric::getLatencyMs)
            .sorted()
            .collect(Collectors.toList());
        
        analytics.setAverageLatencyMs(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
        analytics.setP50LatencyMs(percentile(latencies, 0.50));
        analytics.setP95LatencyMs(percentile(latencies, 0.95));
        analytics.setP99LatencyMs(percentile(latencies, 0.99));
        
        // Intent distribution
        Map<String, Long> queriesByIntent = new HashMap<>();
        Map<String, List<Long>> latencyByIntent = new HashMap<>();
        
        for (QueryMetric metric : recentMetrics) {
            for (String intent : metric.getIntents()) {
                queriesByIntent.put(intent, queriesByIntent.getOrDefault(intent, 0L) + 1);
                latencyByIntent.computeIfAbsent(intent, k -> new ArrayList<>()).add(metric.getLatencyMs());
            }
        }
        
        analytics.setQueriesByIntent(queriesByIntent);
        
        Map<String, Double> avgLatencyByIntent = latencyByIntent.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0)
            ));
        analytics.setAverageLatencyByIntent(avgLatencyByIntent);
        
        // Success rate
        long successful = recentMetrics.stream().filter(QueryMetric::isSuccess).count();
        analytics.setSuccessfulQueries(successful);
        analytics.setFailedQueries(recentMetrics.size() - successful);
        analytics.setSuccessRate(recentMetrics.size() > 0 ? (double) successful / recentMetrics.size() : 0.0);
        
        return analytics;
    }
    
    /**
     * Get performance metrics for the last N hours
     */
    public MonitoringController.PerformanceMetrics getPerformanceMetrics(int lookbackHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(lookbackHours);
        
        List<PerformanceMetric> recentMetrics = performanceMetrics.values().stream()
            .filter(m -> m.getTimestamp().isAfter(cutoff))
            .collect(Collectors.toList());
        
        if (recentMetrics.isEmpty()) {
            return createEmptyPerformanceMetrics();
        }
        
        MonitoringController.PerformanceMetrics metrics = new MonitoringController.PerformanceMetrics();
        metrics.setTotalRequests(recentMetrics.size());
        
        // Aggregate step latencies
        Map<String, List<Long>> stepLatencies = new HashMap<>();
        
        for (PerformanceMetric metric : recentMetrics) {
            for (Map.Entry<String, Long> entry : metric.getStepLatencies().entrySet()) {
                stepLatencies.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        Map<String, Double> avgStepLatencies = stepLatencies.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0)
            ));
        
        metrics.setStepLatencies(avgStepLatencies);
        metrics.setIntentDetectionAvgMs(avgStepLatencies.getOrDefault("intent_detection", 0.0));
        metrics.setVectorSearchAvgMs(avgStepLatencies.getOrDefault("vector_search", 0.0));
        metrics.setLlmGenerationAvgMs(avgStepLatencies.getOrDefault("llm_generation", 0.0));
        metrics.setTotalPipelineAvgMs(avgStepLatencies.getOrDefault("total", 0.0));
        
        return metrics;
    }
    
    /**
     * Get intent analytics for the last N hours
     */
    public MonitoringController.IntentAnalytics getIntentAnalytics(int lookbackHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(lookbackHours);
        
        List<IntentMetric> recentMetrics = intentMetrics.values().stream()
            .filter(m -> m.getTimestamp().isAfter(cutoff))
            .collect(Collectors.toList());
        
        if (recentMetrics.isEmpty()) {
            return createEmptyIntentAnalytics();
        }
        
        MonitoringController.IntentAnalytics analytics = new MonitoringController.IntentAnalytics();
        
        // Intent distribution
        Map<String, Long> distribution = new HashMap<>();
        Map<String, Long> accurateCounts = new HashMap<>();
        
        for (IntentMetric metric : recentMetrics) {
            for (String intent : metric.getIntents()) {
                distribution.put(intent, distribution.getOrDefault(intent, 0L) + 1);
                if (metric.isAccurate()) {
                    accurateCounts.put(intent, accurateCounts.getOrDefault(intent, 0L) + 1);
                }
            }
        }
        
        analytics.setIntentDistribution(distribution);
        
        // Intent accuracy
        Map<String, Double> accuracy = distribution.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    long accurate = accurateCounts.getOrDefault(e.getKey(), 0L);
                    return e.getValue() > 0 ? (double) accurate / e.getValue() : 0.0;
                }
            ));
        analytics.setIntentAccuracy(accuracy);
        
        // Multi-intent statistics
        long multiIntent = recentMetrics.stream()
            .filter(m -> m.getIntents().size() > 1)
            .count();
        
        analytics.setTotalIntentsDetected(recentMetrics.size());
        analytics.setMultiIntentQueries(multiIntent);
        analytics.setMultiIntentRate(recentMetrics.size() > 0 ? (double) multiIntent / recentMetrics.size() : 0.0);
        
        return analytics;
    }
    
    /**
     * Get health summary
     */
    public MonitoringController.HealthSummary getHealthSummary() {
        MonitoringController.HealthSummary summary = new MonitoringController.HealthSummary();
        
        Map<String, String> componentStatus = new HashMap<>();
        componentStatus.put("backend", "UP");
        componentStatus.put("database", "UP");  // Could check actual DB connection
        componentStatus.put("phi4_api", "UP");  // Could check actual Phi-4 health
        
        summary.setComponentStatus(componentStatus);
        summary.setOverallStatus("UP");
        summary.setLastCheck(LocalDateTime.now());
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_queries", totalQueries.get());
        metrics.put("successful_queries", successfulQueries.get());
        metrics.put("failed_queries", failedQueries.get());
        metrics.put("success_rate", totalQueries.get() > 0 ? 
            (double) successfulQueries.get() / totalQueries.get() : 0.0);
        
        summary.setMetrics(metrics);
        
        return summary;
    }
    
    // Helper methods
    
    private double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0.0;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        index = Math.max(0, Math.min(index, values.size() - 1));
        return values.get(index);
    }
    
    private MonitoringController.QueryAnalytics createEmptyQueryAnalytics() {
        MonitoringController.QueryAnalytics analytics = new MonitoringController.QueryAnalytics();
        analytics.setTotalQueries(0);
        analytics.setAverageLatencyMs(0.0);
        analytics.setQueriesByIntent(Collections.emptyMap());
        analytics.setAverageLatencyByIntent(Collections.emptyMap());
        analytics.setSuccessfulQueries(0);
        analytics.setFailedQueries(0);
        analytics.setSuccessRate(0.0);
        return analytics;
    }
    
    private MonitoringController.PerformanceMetrics createEmptyPerformanceMetrics() {
        MonitoringController.PerformanceMetrics metrics = new MonitoringController.PerformanceMetrics();
        metrics.setTotalRequests(0);
        metrics.setStepLatencies(Collections.emptyMap());
        return metrics;
    }
    
    private MonitoringController.IntentAnalytics createEmptyIntentAnalytics() {
        MonitoringController.IntentAnalytics analytics = new MonitoringController.IntentAnalytics();
        analytics.setIntentDistribution(Collections.emptyMap());
        analytics.setIntentAccuracy(Collections.emptyMap());
        analytics.setTotalIntentsDetected(0);
        analytics.setMultiIntentQueries(0);
        analytics.setMultiIntentRate(0.0);
        return analytics;
    }
    
    // Data classes
    
    private static class QueryMetric {
        private final String requestId;
        private final String question;
        private final List<String> intents;
        private final long latencyMs;
        private final boolean success;
        private final LocalDateTime timestamp;
        
        public QueryMetric(String requestId, String question, List<String> intents, 
                          long latencyMs, boolean success, LocalDateTime timestamp) {
            this.requestId = requestId;
            this.question = question;
            this.intents = intents;
            this.latencyMs = latencyMs;
            this.success = success;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public String getQuestion() { return question; }
        public List<String> getIntents() { return intents; }
        public long getLatencyMs() { return latencyMs; }
        public boolean isSuccess() { return success; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private static class PerformanceMetric {
        private final String requestId;
        private final Map<String, Long> stepLatencies;
        private final LocalDateTime timestamp;
        
        public PerformanceMetric(String requestId, Map<String, Long> stepLatencies, LocalDateTime timestamp) {
            this.requestId = requestId;
            this.stepLatencies = stepLatencies;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public Map<String, Long> getStepLatencies() { return stepLatencies; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private static class IntentMetric {
        private final String requestId;
        private final List<String> intents;
        private final boolean accurate;
        private final LocalDateTime timestamp;
        
        public IntentMetric(String requestId, List<String> intents, boolean accurate, LocalDateTime timestamp) {
            this.requestId = requestId;
            this.intents = intents;
            this.accurate = accurate;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public List<String> getIntents() { return intents; }
        public boolean isAccurate() { return accurate; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}

