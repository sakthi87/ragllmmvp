package com.yugabyte.rag.service;

import com.yugabyte.rag.model.RagQueryResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RCA (Root Cause Analysis) Pipeline Service
 * Implements 6-stage structured RCA pipeline:
 * 1. Signal detection (automated)
 * 2. Noise filtering (automated)
 * 3. Correlation ranking (automated)
 * 4. Root cause extraction (structured)
 * 5. Fix recommendation (structured)
 * 6. Confidence scoring
 */
@Service
@Slf4j
public class RcaPipelineService {
    
    /**
     * Execute full RCA pipeline on retrieved documents
     */
    public RcaResult executeRcaPipeline(String question, List<RagQueryResponse.SourceDocument> documents) {
        log.info("🔍 Starting RCA pipeline for question: {}", question);
        
        // Stage 1: Signal Detection (automated)
        List<Signal> signals = detectSignals(documents);
        log.info("   Stage 1: Detected {} signals", signals.size());
        
        // Stage 2: Noise Filtering (automated)
        List<Signal> filteredSignals = filterNoise(signals);
        log.info("   Stage 2: Filtered to {} relevant signals", filteredSignals.size());
        
        // Stage 3: Correlation Ranking (automated)
        List<Signal> rankedSignals = rankByCorrelation(filteredSignals, question);
        log.info("   Stage 3: Ranked {} signals by correlation", rankedSignals.size());
        
        // Stage 4: Root Cause Extraction (structured)
        RootCause rootCause = extractRootCause(rankedSignals, question);
        log.info("   Stage 4: Extracted root cause with confidence: {}", rootCause.getConfidence());
        
        // Stage 5: Fix Recommendation (structured)
        List<FixRecommendation> fixes = generateFixRecommendations(rootCause, rankedSignals);
        log.info("   Stage 5: Generated {} fix recommendations", fixes.size());
        
        // Stage 6: Confidence Scoring
        double overallConfidence = calculateOverallConfidence(rootCause, rankedSignals, fixes);
        log.info("   Stage 6: Overall confidence: {}%", String.format("%.2f", overallConfidence * 100));
        
        return new RcaResult(
            question,
            rootCause,
            rankedSignals,
            fixes,
            overallConfidence
        );
    }
    
    /**
     * Stage 1: Signal Detection (automated)
     * Detects anomalies, errors, and patterns in documents
     */
    private List<Signal> detectSignals(List<RagQueryResponse.SourceDocument> documents) {
        List<Signal> signals = new ArrayList<>();
        
        // Error keywords
        Set<String> errorKeywords = Set.of(
            "error", "exception", "failed", "failure", "timeout", "crash",
            "outofmemory", "nullpointer", "connection refused", "503", "500",
            "latency spike", "throughput drop", "lag", "delayed"
        );
        
        // Anomaly keywords
        Set<String> anomalyKeywords = Set.of(
            "unusual", "abnormal", "spike", "drop", "increase", "decrease",
            "threshold exceeded", "above normal", "below normal"
        );
        
        for (RagQueryResponse.SourceDocument doc : documents) {
            String content = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
            String sourceType = doc.getSourceType();
            
            // Detect error signals
            for (String keyword : errorKeywords) {
                if (content.contains(keyword)) {
                    signals.add(new Signal(
                        SignalType.ERROR,
                        keyword,
                        doc,
                        extractContext(content, keyword),
                        calculateSignalStrength(content, keyword)
                    ));
                }
            }
            
            // Detect anomaly signals
            for (String keyword : anomalyKeywords) {
                if (content.contains(keyword)) {
                    signals.add(new Signal(
                        SignalType.ANOMALY,
                        keyword,
                        doc,
                        extractContext(content, keyword),
                        calculateSignalStrength(content, keyword)
                    ));
                }
            }
            
            // Detect metric threshold violations
            if (sourceType != null && sourceType.equals("METRIC_SUMMARY")) {
                if (content.contains("threshold") || content.contains("exceeded")) {
                    signals.add(new Signal(
                        SignalType.THRESHOLD_VIOLATION,
                        "metric_threshold",
                        doc,
                        extractContext(content, "threshold"),
                        0.8
                    ));
                }
            }
        }
        
        return signals;
    }
    
    /**
     * Stage 2: Noise Filtering (automated)
     * Filters out low-confidence or irrelevant signals
     */
    private List<Signal> filterNoise(List<Signal> signals) {
        return signals.stream()
            .filter(signal -> signal.getStrength() >= 0.5)  // Minimum confidence threshold
            .filter(signal -> signal.getContext() != null && signal.getContext().length() > 10)  // Must have context
            .collect(Collectors.toList());
    }
    
    /**
     * Stage 3: Correlation Ranking (automated)
     * Ranks signals by correlation with the question and temporal proximity
     */
    private List<Signal> rankByCorrelation(List<Signal> signals, String question) {
        String lowerQuestion = question.toLowerCase();
        
        // Calculate correlation score for each signal
        signals.forEach(signal -> {
            double correlationScore = 0.0;
            
            // Keyword matching with question
            String[] questionWords = lowerQuestion.split("\\s+");
            String signalContext = signal.getContext().toLowerCase();
            
            for (String word : questionWords) {
                if (word.length() > 3 && signalContext.contains(word)) {
                    correlationScore += 0.1;
                }
            }
            
            // Boost for error-related questions
            if (lowerQuestion.contains("error") || lowerQuestion.contains("fail")) {
                if (signal.getType() == SignalType.ERROR) {
                    correlationScore += 0.3;
                }
            }
            
            // Boost for performance-related questions
            if (lowerQuestion.contains("slow") || lowerQuestion.contains("latency") || lowerQuestion.contains("performance")) {
                if (signal.getType() == SignalType.ANOMALY || signal.getType() == SignalType.THRESHOLD_VIOLATION) {
                    correlationScore += 0.3;
                }
            }
            
            // Combine with signal strength
            signal.setCorrelationScore(Math.min(1.0, correlationScore + signal.getStrength()));
        });
        
        // Sort by correlation score (descending)
        return signals.stream()
            .sorted((a, b) -> Double.compare(b.getCorrelationScore(), a.getCorrelationScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * Stage 4: Root Cause Extraction (structured)
     * Extracts the most likely root cause from ranked signals
     */
    private RootCause extractRootCause(List<Signal> rankedSignals, String question) {
        if (rankedSignals.isEmpty()) {
            return new RootCause(
                "No root cause identified",
                "Insufficient evidence in retrieved documents",
                Collections.emptyList(),
                0.0
            );
        }
        
        // Top signal is most likely root cause
        Signal topSignal = rankedSignals.get(0);
        
        // Build supporting evidence from top 3 signals
        List<String> evidence = rankedSignals.stream()
            .limit(3)
            .map(s -> String.format("%s: %s (from %s)", 
                s.getType(), 
                s.getContext().substring(0, Math.min(100, s.getContext().length())),
                s.getDocument().getSourceName()))
            .collect(Collectors.toList());
        
        // Generate root cause description
        String description = generateRootCauseDescription(topSignal, rankedSignals);
        
        // Calculate confidence based on signal strength and correlation
        double confidence = (topSignal.getCorrelationScore() + topSignal.getStrength()) / 2.0;
        
        return new RootCause(
            description,
            topSignal.getContext(),
            evidence,
            confidence
        );
    }
    
    /**
     * Stage 5: Fix Recommendation (structured)
     * Generates actionable fix recommendations based on root cause
     */
    private List<FixRecommendation> generateFixRecommendations(RootCause rootCause, List<Signal> signals) {
        List<FixRecommendation> fixes = new ArrayList<>();
        
        if (signals.isEmpty()) {
            return fixes;
        }
        
        Signal topSignal = signals.get(0);
        
        // Generate recommendations based on signal type
        switch (topSignal.getType()) {
            case ERROR:
                fixes.add(new FixRecommendation(
                    "Check application logs",
                    "Review detailed error logs for the specific error message",
                    "HIGH",
                    "Immediate"
                ));
                fixes.add(new FixRecommendation(
                    "Verify system resources",
                    "Check CPU, memory, and disk usage at the time of error",
                    "MEDIUM",
                    "Within 1 hour"
                ));
                break;
                
            case ANOMALY:
                fixes.add(new FixRecommendation(
                    "Investigate metric trends",
                    "Compare current metrics with historical baselines",
                    "HIGH",
                    "Within 30 minutes"
                ));
                fixes.add(new FixRecommendation(
                    "Check dependent services",
                    "Verify health of upstream/downstream services",
                    "MEDIUM",
                    "Within 1 hour"
                ));
                break;
                
            case THRESHOLD_VIOLATION:
                fixes.add(new FixRecommendation(
                    "Review threshold configuration",
                    "Verify if thresholds are set appropriately",
                    "HIGH",
                    "Immediate"
                ));
                fixes.add(new FixRecommendation(
                    "Scale resources if needed",
                    "Consider scaling if threshold violations are persistent",
                    "MEDIUM",
                    "Within 2 hours"
                ));
                break;
        }
        
        // Add preventive action
        fixes.add(new FixRecommendation(
            "Implement monitoring alerts",
            "Set up proactive alerts for similar issues",
            "LOW",
            "Within 24 hours"
        ));
        
        return fixes;
    }
    
    /**
     * Stage 6: Confidence Scoring
     * Calculates overall confidence in the RCA result
     */
    private double calculateOverallConfidence(RootCause rootCause, List<Signal> signals, List<FixRecommendation> fixes) {
        if (signals.isEmpty()) {
            return 0.0;
        }
        
        // Base confidence from root cause
        double baseConfidence = rootCause.getConfidence();
        
        // Boost for multiple signals (more evidence = higher confidence)
        double signalBoost = Math.min(0.2, signals.size() * 0.05);
        
        // Boost for fix recommendations (actionable = higher confidence)
        double fixBoost = Math.min(0.1, fixes.size() * 0.02);
        
        return Math.min(1.0, baseConfidence + signalBoost + fixBoost);
    }
    
    // Helper methods
    
    private String extractContext(String content, String keyword) {
        int index = content.indexOf(keyword);
        if (index == -1) return "";
        
        int start = Math.max(0, index - 100);
        int end = Math.min(content.length(), index + keyword.length() + 100);
        return content.substring(start, end).trim();
    }
    
    private double calculateSignalStrength(String content, String keyword) {
        // Count occurrences (more occurrences = stronger signal)
        long count = content.split(keyword, -1).length - 1;
        return Math.min(1.0, 0.5 + (count * 0.1));
    }
    
    private String generateRootCauseDescription(Signal topSignal, List<Signal> allSignals) {
        StringBuilder desc = new StringBuilder();
        
        desc.append("Root cause identified: ");
        desc.append(topSignal.getType().name().toLowerCase().replace("_", " "));
        desc.append(" detected in ");
        desc.append(topSignal.getDocument().getSourceType());
        
        if (allSignals.size() > 1) {
            desc.append(" (supported by ");
            desc.append(allSignals.size() - 1);
            desc.append(" additional signal");
            if (allSignals.size() > 2) desc.append("s");
            desc.append(")");
        }
        
        return desc.toString();
    }
    
    // Data classes
    
    @Data
    public static class RcaResult {
        private final String question;
        private final RootCause rootCause;
        private final List<Signal> signals;
        private final List<FixRecommendation> fixes;
        private final double overallConfidence;
    }
    
    @Data
    public static class Signal {
        private final SignalType type;
        private final String keyword;
        private final RagQueryResponse.SourceDocument document;
        private final String context;
        private final double strength;
        private double correlationScore = 0.0;
    }
    
    public enum SignalType {
        ERROR,
        ANOMALY,
        THRESHOLD_VIOLATION
    }
    
    @Data
    public static class RootCause {
        private final String description;
        private final String details;
        private final List<String> evidence;
        private final double confidence;
    }
    
    @Data
    public static class FixRecommendation {
        private final String action;
        private final String description;
        private final String priority;
        private final String timeframe;
    }
}

