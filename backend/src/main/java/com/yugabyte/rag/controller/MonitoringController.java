package com.yugabyte.rag.controller;

import com.yugabyte.rag.service.MetricsService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitoring Controller for RAG System
 * Provides endpoints for:
 * - Query analytics
 * - Performance metrics
 * - Intent detection accuracy
 */
@RestController
@RequestMapping("/monitoring")
@CrossOrigin(origins = "*")
@Slf4j
public class MonitoringController {
    
    private final MetricsService metricsService;
    
    public MonitoringController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    /**
     * Get query analytics
     * Returns statistics about queries: count, average latency, intent distribution, etc.
     */
    @GetMapping("/analytics/queries")
    public ResponseEntity<QueryAnalytics> getQueryAnalytics(
            @RequestParam(required = false) Integer hours) {
        int lookbackHours = hours != null ? hours : 24;  // Default: last 24 hours
        
        QueryAnalytics analytics = metricsService.getQueryAnalytics(lookbackHours);
        return ResponseEntity.ok(analytics);
    }
    
    /**
     * Get performance metrics
     * Returns latency breakdown by step: intent detection, vector search, LLM generation, etc.
     */
    @GetMapping("/metrics/performance")
    public ResponseEntity<PerformanceMetrics> getPerformanceMetrics(
            @RequestParam(required = false) Integer hours) {
        int lookbackHours = hours != null ? hours : 24;
        
        PerformanceMetrics metrics = metricsService.getPerformanceMetrics(lookbackHours);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get intent detection accuracy
     * Returns statistics about intent detection: accuracy, distribution, etc.
     */
    @GetMapping("/analytics/intents")
    public ResponseEntity<IntentAnalytics> getIntentAnalytics(
            @RequestParam(required = false) Integer hours) {
        int lookbackHours = hours != null ? hours : 24;
        
        IntentAnalytics analytics = metricsService.getIntentAnalytics(lookbackHours);
        return ResponseEntity.ok(analytics);
    }
    
    /**
     * Get system health summary
     * Returns overall system health with component status
     */
    @GetMapping("/health/summary")
    public ResponseEntity<HealthSummary> getHealthSummary() {
        HealthSummary summary = metricsService.getHealthSummary();
        return ResponseEntity.ok(summary);
    }
    
    // Data classes
    
    @Data
    public static class QueryAnalytics {
        private long totalQueries;
        private double averageLatencyMs;
        private double p50LatencyMs;
        private double p95LatencyMs;
        private double p99LatencyMs;
        private Map<String, Long> queriesByIntent;
        private Map<String, Double> averageLatencyByIntent;
        private long successfulQueries;
        private long failedQueries;
        private double successRate;
    }
    
    @Data
    public static class PerformanceMetrics {
        private double intentDetectionAvgMs;
        private double vectorSearchAvgMs;
        private double llmGenerationAvgMs;
        private double totalPipelineAvgMs;
        private Map<String, Double> stepLatencies;
        private long totalRequests;
    }
    
    @Data
    public static class IntentAnalytics {
        private Map<String, Long> intentDistribution;
        private Map<String, Double> intentAccuracy;
        private long totalIntentsDetected;
        private long multiIntentQueries;
        private double multiIntentRate;
    }
    
    @Data
    public static class HealthSummary {
        private String overallStatus;
        private Map<String, String> componentStatus;
        private LocalDateTime lastCheck;
        private Map<String, Object> metrics;
    }
}

