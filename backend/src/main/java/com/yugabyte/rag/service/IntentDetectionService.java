package com.yugabyte.rag.service;

import com.yugabyte.rag.config.IntentConfigLoader;
import com.yugabyte.rag.config.QueryRewriteConfigLoader;
import com.yugabyte.rag.model.DetectedIntent;
import com.yugabyte.rag.model.IntentRule;
import com.yugabyte.rag.model.QueryRewriteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for detecting document types (intents) from user questions.
 * Uses JSON-based intent configuration (rag-intents.json) with fallback to hardcoded rules.
 * Maps keywords to doc_sub_type values for targeted vector search.
 */
@Service
@Slf4j
public class IntentDetectionService {
    
    private final IntentConfigLoader intentConfigLoader;
    private final QueryRewriteConfigLoader queryRewriteConfigLoader;
    
    @Autowired
    public IntentDetectionService(IntentConfigLoader intentConfigLoader,
                                   QueryRewriteConfigLoader queryRewriteConfigLoader) {
        this.intentConfigLoader = intentConfigLoader;
        this.queryRewriteConfigLoader = queryRewriteConfigLoader;
    }
    
    // Fallback: Map of keywords to document types (source_type values)
    // Used only if JSON config is not loaded
    // Using HashMap because Map.of() has limit of 10 pairs
    private static final Map<String, List<String>> INTENT_DOC_TYPE_MAP = new HashMap<>();
    
    static {
        // Schema-related queries → METADATA with doc_sub_type = schema_metadata
        INTENT_DOC_TYPE_MAP.put("schema", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("primary key", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("columns", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("table structure", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("ddl", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("create table", Arrays.asList("METADATA"));
        
        // Business metadata queries → METADATA with doc_sub_type = business_metadata
        INTENT_DOC_TYPE_MAP.put("domain", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("owner", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("pii", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("data owner", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("business", Arrays.asList("METADATA"));
        
        // Storage configuration queries → METADATA with doc_sub_type = storage_configuration
        INTENT_DOC_TYPE_MAP.put("tombstone", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("compaction", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("storage", Arrays.asList("METADATA"));
        
        // Data lifecycle queries → METADATA with doc_sub_type = data_lifecycle
        INTENT_DOC_TYPE_MAP.put("ttl", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("lifecycle", Arrays.asList("METADATA"));
        INTENT_DOC_TYPE_MAP.put("retention", Arrays.asList("METADATA"));
        
        // Lineage queries
        INTENT_DOC_TYPE_MAP.put("lineage", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("populated", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("kafka topic", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("spark job", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("pipeline", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("data flow", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("which api", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("reads from", Arrays.asList("LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("writes to", Arrays.asList("LINEAGE"));
        
        // Log queries
        INTENT_DOC_TYPE_MAP.put("failure", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("error", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("exception", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("failed", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("outofmemory", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("log", Arrays.asList("LOG_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("yesterday", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("delayed", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY"));
        
        // Metric queries
        INTENT_DOC_TYPE_MAP.put("latency", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("lag", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("throughput", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("performance", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("metric", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("slow", Arrays.asList("METRIC_SUMMARY"));
        INTENT_DOC_TYPE_MAP.put("bottleneck", Arrays.asList("METRIC_SUMMARY"));
        
        // RCA queries (multiple types)
        INTENT_DOC_TYPE_MAP.put("why", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("root cause", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("what caused", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"));
        INTENT_DOC_TYPE_MAP.put("rca", Arrays.asList("LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"));
    }
    
    /**
     * Map keywords to doc_sub_type for fine-grained filtering.
     * Supports all 12 canonical document types:
     * - business_metadata, schema_metadata, storage_configuration, table_statistics, data_lifecycle
     * - lineage_kafka, lineage_spark, lineage_dataapi
     * - logs_daily, logs_weekly, metrics_daily, metrics_weekly
     */
    private static final Map<String, String> KEYWORD_TO_DOC_SUB_TYPE = new HashMap<>();
    
    static {
        // Schema metadata
        KEYWORD_TO_DOC_SUB_TYPE.put("schema", "schema_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("primary key", "schema_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("columns", "schema_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("ddl", "schema_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("create table", "schema_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("table structure", "schema_metadata");
        
        // Business metadata
        KEYWORD_TO_DOC_SUB_TYPE.put("domain", "business_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("owner", "business_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("pii", "business_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("data owner", "business_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("business", "business_metadata");
        KEYWORD_TO_DOC_SUB_TYPE.put("steward", "business_metadata");
        
        // Storage configuration
        KEYWORD_TO_DOC_SUB_TYPE.put("tombstone", "storage_configuration");
        KEYWORD_TO_DOC_SUB_TYPE.put("compaction", "storage_configuration");
        KEYWORD_TO_DOC_SUB_TYPE.put("storage", "storage_configuration");
        KEYWORD_TO_DOC_SUB_TYPE.put("bloom filter", "storage_configuration");
        KEYWORD_TO_DOC_SUB_TYPE.put("gc grace", "storage_configuration");
        KEYWORD_TO_DOC_SUB_TYPE.put("caching", "storage_configuration");
        
        // Table statistics
        KEYWORD_TO_DOC_SUB_TYPE.put("table size", "table_statistics");
        KEYWORD_TO_DOC_SUB_TYPE.put("row count", "table_statistics");
        KEYWORD_TO_DOC_SUB_TYPE.put("partition count", "table_statistics");
        KEYWORD_TO_DOC_SUB_TYPE.put("sstable", "table_statistics");
        KEYWORD_TO_DOC_SUB_TYPE.put("statistics", "table_statistics");
        KEYWORD_TO_DOC_SUB_TYPE.put("size on disk", "table_statistics");
        
        // Data lifecycle
        KEYWORD_TO_DOC_SUB_TYPE.put("ttl", "data_lifecycle");
        KEYWORD_TO_DOC_SUB_TYPE.put("lifecycle", "data_lifecycle");
        KEYWORD_TO_DOC_SUB_TYPE.put("retention", "data_lifecycle");
        KEYWORD_TO_DOC_SUB_TYPE.put("archive", "data_lifecycle");
        KEYWORD_TO_DOC_SUB_TYPE.put("purge", "data_lifecycle");
        
        // Lineage - Kafka
        KEYWORD_TO_DOC_SUB_TYPE.put("kafka topic", "lineage_kafka");
        KEYWORD_TO_DOC_SUB_TYPE.put("kafka", "lineage_kafka");
        KEYWORD_TO_DOC_SUB_TYPE.put("topic", "lineage_kafka");
        
        // Lineage - Spark
        KEYWORD_TO_DOC_SUB_TYPE.put("spark job", "lineage_spark");
        KEYWORD_TO_DOC_SUB_TYPE.put("spark", "lineage_spark");
        KEYWORD_TO_DOC_SUB_TYPE.put("streaming", "lineage_spark");
        
        // Lineage - DataAPI
        KEYWORD_TO_DOC_SUB_TYPE.put("api", "lineage_dataapi");
        KEYWORD_TO_DOC_SUB_TYPE.put("endpoint", "lineage_dataapi");
        KEYWORD_TO_DOC_SUB_TYPE.put("dataapi", "lineage_dataapi");
    }
    
    /**
     * Detect time scope from question to route to daily vs. weekly documents.
     * 
     * @param question User's question
     * @return "daily" or "weekly" or null if not detected
     */
    public String detectTimeScope(String question) {
        if (question == null || question.trim().isEmpty()) {
            return null;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        // Daily keywords
        if (lowerQuestion.contains("today") || lowerQuestion.contains("yesterday") || 
            lowerQuestion.contains("last 24h") || lowerQuestion.contains("last 24 hours") ||
            lowerQuestion.contains("now") || lowerQuestion.contains("currently") ||
            lowerQuestion.contains("recent") || lowerQuestion.contains("just now")) {
            return "daily";
        }
        
        // Weekly keywords
        if (lowerQuestion.contains("this week") || lowerQuestion.contains("last 7 days") ||
            lowerQuestion.contains("last week") || lowerQuestion.contains("7 days") ||
            lowerQuestion.contains("weekly") || lowerQuestion.contains("week")) {
            return "weekly";
        }
        
        return null;
    }
    
    /**
     * Detect component from question for component-specific lineage routing.
     * 
     * @param question User's question
     * @return Component name (kafka, spark, dataapi) or null
     */
    public String detectComponent(String question) {
        if (question == null || question.trim().isEmpty()) {
            return null;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        if (lowerQuestion.contains("kafka")) {
            return "kafka";
        }
        if (lowerQuestion.contains("spark")) {
            return "spark";
        }
        if (lowerQuestion.contains("api") || lowerQuestion.contains("dataapi")) {
            return "dataapi";
        }
        
        return null;
    }
    
    /**
     * Detect document types from user question using JSON-based intent rules.
     * Returns list of source_type values to search in vector DB.
     * 
     * @param question User's question
     * @return List of detected document types (source_type values)
     */
    public List<String> detectIntents(String question) {
        if (question == null || question.trim().isEmpty()) {
            log.warn("Empty question provided for intent detection");
            return Arrays.asList("METADATA", "LINEAGE", "LOG_SUMMARY", "METRIC_SUMMARY");
        }
        
        // Use JSON-based detection if available, otherwise fallback to hardcoded
        List<DetectedIntent> detectedIntents = detectIntentsWithDetails(question);
        
        // Extract unique source_type values
        Set<String> sourceTypes = new LinkedHashSet<>();
        for (DetectedIntent intent : detectedIntents) {
            if (intent.getSourceType() != null) {
                sourceTypes.add(intent.getSourceType());
            }
        }
        
        // If no specific intent detected, default to all types
        if (sourceTypes.isEmpty()) {
            log.info("No specific intent detected for question: '{}', defaulting to all types", question);
            sourceTypes.addAll(Arrays.asList("METADATA", "LINEAGE", "LOG_SUMMARY", "METRIC_SUMMARY"));
        }
        
        List<String> result = new ArrayList<>(sourceTypes);
        log.info("Detected intents for question '{}': {}", question, result);
        return result;
    }
    
    /**
     * Detect intents with full details (doc_type, time_window, source_type).
     * Uses JSON-based rules if available, falls back to hardcoded rules.
     * 
     * @param question User's question
     * @return List of detected intents with details
     */
    public List<DetectedIntent> detectIntentsWithDetails(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String lowerQuestion = question.toLowerCase();
        List<DetectedIntent> detectedIntents = new ArrayList<>();
        
        // Try JSON-based detection first
        if (intentConfigLoader.isLoaded()) {
            for (IntentRule rule : intentConfigLoader.getRules()) {
                for (String keyword : rule.getKeywords()) {
                    if (lowerQuestion.contains(keyword.toLowerCase())) {
                        DetectedIntent intent = new DetectedIntent(
                            rule.getDocType(),
                            rule.getTimeWindowDays()
                        );
                        detectedIntents.add(intent);
                        log.debug("Detected intent '{}' (doc_type: {}) for keyword '{}'", 
                                rule.getIntentName(), rule.getDocType(), keyword);
                        break;  // One match per rule is enough
                    }
                }
            }
        }
        
        // Fallback to hardcoded rules if JSON not loaded or no matches
        if (detectedIntents.isEmpty() && !intentConfigLoader.isLoaded()) {
            log.debug("Using fallback hardcoded intent detection");
            Set<String> detectedSourceTypes = new LinkedHashSet<>();
            
            for (Map.Entry<String, List<String>> entry : INTENT_DOC_TYPE_MAP.entrySet()) {
                String keyword = entry.getKey();
                if (lowerQuestion.contains(keyword)) {
                    detectedSourceTypes.addAll(entry.getValue());
                }
            }
            
            // Convert source_types to DetectedIntent (with doc_sub_type detection)
            for (String sourceType : detectedSourceTypes) {
                String docSubType = detectDocSubType(question, sourceType);
                if (docSubType != null) {
                    Integer timeWindow = null;
                    if (docSubType.contains("daily")) {
                        timeWindow = 1;
                    } else if (docSubType.contains("weekly")) {
                        timeWindow = 7;
                    }
                    detectedIntents.add(new DetectedIntent(docSubType, timeWindow));
                } else {
                    // Create intent with just source_type
                    detectedIntents.add(new DetectedIntent(null, null, sourceType));
                }
            }
        }
        
        return detectedIntents;
    }
    
    /**
     * Detect doc_sub_type from question for fine-grained filtering.
     * Uses JSON-based intent rules with example_questions matching if available, falls back to hardcoded logic.
     * Handles all 12 canonical document types including time-scoped and component-specific types.
     * 
     * @param question User's question
     * @param sourceType The detected source_type (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
     * @return doc_sub_type value or null if not detected
     */
    public String detectDocSubType(String question, String sourceType) {
        if (question == null || question.trim().isEmpty()) {
            return null;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        // Try JSON-based detection using example_questions first (most accurate)
        if (queryRewriteConfigLoader.isLoaded()) {
            // Check example_questions for semantic similarity
            String bestMatch = findBestMatchByExampleQuestions(question, sourceType);
            if (bestMatch != null) {
                log.debug("Detected doc_sub_type '{}' from example_questions matching", bestMatch);
                return bestMatch;
            }
        }
        
        // Try JSON-based detection using keywords (fallback)
        if (intentConfigLoader.isLoaded()) {
            for (IntentRule rule : intentConfigLoader.getRules()) {
                // Only check rules that match the source_type
                DetectedIntent intent = new DetectedIntent(rule.getDocType(), rule.getTimeWindowDays());
                if (sourceType != null && !sourceType.equals(intent.getSourceType())) {
                    continue;  // Skip rules that don't match source_type
                }
                
                // Check if any keyword matches
                for (String keyword : rule.getKeywords()) {
                    if (lowerQuestion.contains(keyword.toLowerCase())) {
                        log.debug("Detected doc_sub_type '{}' from JSON rule '{}' for keyword '{}'", 
                                rule.getDocType(), rule.getIntentName(), keyword);
                        return rule.getDocType();
                    }
                }
            }
        }
        
        // Fallback to hardcoded logic
        String timeScope = detectTimeScope(question);
        String component = detectComponent(question);
        
        // Handle time-scoped log/metric types
        if ("LOG_SUMMARY".equals(sourceType)) {
            if (timeScope != null) {
                return "logs_" + timeScope;
            }
            // Default to daily if time scope not detected but log-related keywords found
            return "logs_daily";
        }
        
        if ("METRIC_SUMMARY".equals(sourceType)) {
            if (timeScope != null) {
                return "metrics_" + timeScope;
            }
            // Default to daily if time scope not detected but metric-related keywords found
            return "metrics_daily";
        }
        
        // Handle component-specific lineage types
        if ("LINEAGE".equals(sourceType)) {
            if (component != null) {
                return "lineage_" + component;
            }
            // Default lineage type if component not detected
            return null;  // Will search all lineage types
        }
        
        // Handle METADATA sub-types
        if ("METADATA".equals(sourceType)) {
            for (Map.Entry<String, String> entry : KEYWORD_TO_DOC_SUB_TYPE.entrySet()) {
                if (lowerQuestion.contains(entry.getKey())) {
                    String detectedSubType = entry.getValue();
                    // Only return if it's a METADATA sub-type (not lineage)
                    if (detectedSubType.startsWith("business_") || detectedSubType.startsWith("schema_") ||
                        detectedSubType.startsWith("storage_") || detectedSubType.startsWith("table_") ||
                        detectedSubType.startsWith("data_")) {
                        log.debug("Detected doc_sub_type '{}' for keyword '{}' (fallback)", detectedSubType, entry.getKey());
                        return detectedSubType;
                    }
                }
            }
        }
        
        return null;  // No specific sub-type detected
    }
    
    /**
     * Find best matching doc_sub_type by comparing question to example_questions.
     * Uses simple keyword overlap scoring for now (can be enhanced with embedding similarity).
     * 
     * @param question User's question
     * @param sourceType Optional source_type filter
     * @return Best matching doc_sub_type or null
     */
    private String findBestMatchByExampleQuestions(String question, String sourceType) {
        if (!queryRewriteConfigLoader.isLoaded()) {
            return null;
        }
        
        String lowerQuestion = question.toLowerCase();
        String bestMatch = null;
        int bestScore = 0;
        
        // Score each template by example_questions overlap
        for (QueryRewriteTemplate template : queryRewriteConfigLoader.getTemplateMap().values()) {
            // Filter by source_type if provided
            if (sourceType != null && !sourceType.equals(template.getSourceType())) {
                continue;
            }
            
            // Check example_questions for keyword overlap
            if (template.getExampleQuestions() != null) {
                for (String example : template.getExampleQuestions()) {
                    String lowerExample = example.toLowerCase();
                    int score = calculateKeywordOverlap(lowerQuestion, lowerExample);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = template.getDocSubType();
                    }
                }
            }
        }
        
        // Only return if score is above threshold (at least 2 keywords match)
        return bestScore >= 2 ? bestMatch : null;
    }
    
    /**
     * Calculate keyword overlap score between two strings.
     * Simple word-based matching (can be enhanced with TF-IDF or embeddings).
     * 
     * @param question User question (lowercase)
     * @param example Example question (lowercase)
     * @return Overlap score (number of matching significant words)
     */
    private int calculateKeywordOverlap(String question, String example) {
        // Extract significant words (ignore stop words)
        Set<String> stopWords = Set.of("the", "a", "an", "is", "are", "was", "were", "for", "of", "to", "in", "on", "at");
        
        String[] questionWords = question.split("\\s+");
        String[] exampleWords = example.split("\\s+");
        
        Set<String> questionSet = new HashSet<>();
        Set<String> exampleSet = new HashSet<>();
        
        for (String word : questionWords) {
            word = word.replaceAll("[^a-z0-9]", "");
            if (word.length() > 2 && !stopWords.contains(word)) {
                questionSet.add(word);
            }
        }
        
        for (String word : exampleWords) {
            word = word.replaceAll("[^a-z0-9]", "");
            if (word.length() > 2 && !stopWords.contains(word)) {
                exampleSet.add(word);
            }
        }
        
        // Count overlapping words
        int overlap = 0;
        for (String word : questionSet) {
            if (exampleSet.contains(word)) {
                overlap++;
            }
        }
        
        return overlap;
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use detectDocSubType(String question, String sourceType) instead
     */
    @Deprecated
    public String detectDocSubType(String question) {
        return detectDocSubType(question, null);
    }
    
    /**
     * Check if question is a schema-related query.
     */
    public boolean isSchemaQuery(String question) {
        if (question == null) return false;
        String lower = question.toLowerCase();
        return lower.contains("schema") || lower.contains("primary key") || 
               lower.contains("columns") || lower.contains("ddl") ||
               lower.contains("table structure");
    }
    
    /**
     * Check if question is an RCA (Root Cause Analysis) query.
     */
    public boolean isRcaQuery(String question) {
        if (question == null) return false;
        String lower = question.toLowerCase();
        return lower.contains("why") || lower.contains("root cause") || 
               lower.contains("what caused") || lower.contains("rca") ||
               lower.contains("delayed") || lower.contains("bottleneck");
    }
}

