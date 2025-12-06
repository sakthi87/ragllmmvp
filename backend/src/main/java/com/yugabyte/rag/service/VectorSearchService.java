package com.yugabyte.rag.service;

import com.yugabyte.rag.client.Phi4Client;
import com.yugabyte.rag.model.RagQueryResponse;
import com.yugabyte.rag.repository.RagDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for vector similarity search in Yugabyte PGVector.
 * Supports multi-document-type search with similarity threshold filtering.
 */
@Service
@Slf4j
public class VectorSearchService {
    
    private final RagDocumentRepository repository;
    private final Phi4Client phi4Client;
    private final QueryRewriteService queryRewriteService;
    
    @Value("${rag.default-top-k:6}")
    private Integer defaultTopK;
    
    @Value("${rag.max-top-k:10}")
    private Integer maxTopK;
    
    @Value("${rag.similarity-threshold:0.65}")
    private Double defaultSimilarityThreshold;
    
    @Value("${rag.keyspace-filter:transaction_keyspace}")
    private String defaultKeyspace;
    
    @Value("${rag.table-filter:dda_transactions}")
    private String defaultTable;
    
    @Value("${rag.cluster-filter:}")
    private String defaultCluster;
    
    private final IntentDetectionService intentService;
    
    public VectorSearchService(RagDocumentRepository repository, Phi4Client phi4Client, 
                               IntentDetectionService intentService,
                               QueryRewriteService queryRewriteService) {
        this.repository = repository;
        this.phi4Client = phi4Client;
        this.intentService = intentService;
        this.queryRewriteService = queryRewriteService;
    }
    
    /**
     * Search for similar documents across multiple document types.
     * 
     * @param question User question
     * @param docTypes List of source_type values to search
     * @param tableName Optional table name filter
     * @param keyspace Optional keyspace filter
     * @param clusterName Optional cluster name filter (✅ GAP 1 FIX)
     * @param topK Number of results per doc type
     * @return List of retrieved documents with similarity scores
     */
    public List<RagQueryResponse.SourceDocument> searchVectors(
            String question, 
            List<String> docTypes,
            String tableName,
            String keyspace,
            String clusterName,
            Integer topK,
            Integer daysBack) {
        
        if (question == null || question.trim().isEmpty()) {
            log.warn("Empty question provided for vector search");
            return Collections.emptyList();
        }
        
        if (docTypes == null || docTypes.isEmpty()) {
            log.warn("No document types provided, defaulting to all types");
            docTypes = Arrays.asList("METADATA", "LINEAGE", "LOG_SUMMARY", "METRIC_SUMMARY");
        }
        
        // Use defaults if not provided
        String searchTable = tableName != null ? tableName : defaultTable;
        String searchKeyspace = keyspace != null ? keyspace : defaultKeyspace;
        // Pass null instead of empty string to allow COALESCE to work in SQL
        String searchCluster = (clusterName != null && !clusterName.isEmpty()) ? clusterName : 
                              (defaultCluster != null && !defaultCluster.isEmpty() ? defaultCluster : null);
        Integer searchTopK = topK != null ? Math.min(topK, maxTopK) : defaultTopK;
        
        // ✅ GAP 2 FIX: Detect doc_sub_type for fine-grained filtering
        // Detect doc_sub_type for each source_type to support all 12 canonical document types
        log.info("Vector search: question='{}', docTypes={}, cluster={}, table={}, keyspace={}, topK={}", 
                question, docTypes, searchCluster, searchTable, searchKeyspace, searchTopK);
        
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            log.info("═══════════════════════════════════════════════════════════════");
            
            // ✅ P0.1 FIX: Multi-Intent Query Decomposition
            // Step 1: Detect doc_sub_type for each docType (per-intent detection)
            Map<String, String> docTypeToSubType = new HashMap<>();
            for (String docType : docTypes) {
                String subType = intentService.detectDocSubType(question, docType);
                if (subType != null) {
                    docTypeToSubType.put(docType, subType);
                    log.info("   Detected intent: source_type={}, doc_sub_type={}", docType, subType);
                } else {
                    log.warn("   No doc_sub_type detected for source_type={}, will use original question", docType);
                }
            }
            
            // Step 2: Per-Intent Query Rewriting & Embedding Generation
            long rewriteEmbedStart = System.currentTimeMillis();
            log.info("🔵 Step 4️⃣-5️⃣: Per-Intent Query Rewriting & Embedding Generation - STARTED [{}]", 
                    java.time.LocalDateTime.now().format(formatter));
            
            // Map: docType -> (rewrittenQuery, embeddingStr)
            Map<String, IntentEmbedding> intentEmbeddings = new HashMap<>();
            
            for (String docType : docTypes) {
                String subType = docTypeToSubType.get(docType);
                
                // Rewrite query for this specific intent
                long rewriteStart = System.currentTimeMillis();
                String rewrittenQuery = queryRewriteService.rewriteQuery(
                    question, subType, searchKeyspace, searchTable
                );
                long rewriteDuration = System.currentTimeMillis() - rewriteStart;
                
                log.info("   [{}] Original: '{}'", docType, question.length() > 80 ? question.substring(0, 80) + "..." : question);
                log.info("   [{}] Rewritten: '{}' (doc_sub_type: {})", docType, 
                        rewrittenQuery.length() > 100 ? rewrittenQuery.substring(0, 100) + "..." : rewrittenQuery, subType);
                
                // Generate embedding for this rewritten query
                long embedStart = System.currentTimeMillis();
                List<Double> queryEmbedding = phi4Client.generateEmbedding(rewrittenQuery);
                String embeddingStr = formatEmbedding(queryEmbedding);
                long embedDuration = System.currentTimeMillis() - embedStart;
                
                intentEmbeddings.put(docType, new IntentEmbedding(rewrittenQuery, embeddingStr, subType));
                log.info("   [{}] Embedding generated: dimension={}, duration={}ms", 
                        docType, queryEmbedding.size(), embedDuration);
            }
            
            long totalRewriteEmbedTime = System.currentTimeMillis() - 
                    java.time.LocalDateTime.now().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() +
                    System.currentTimeMillis();
            log.info("✅ Step 4️⃣-5️⃣: Per-Intent Query Rewriting & Embedding Generation - COMPLETED [{}] (Total: {}ms)", 
                    java.time.LocalDateTime.now().format(formatter), totalRewriteEmbedTime);
            
            // Step 3: Per-Intent Vector Search (each docType uses its own embedding)
            log.info("🔵 Step 6️⃣: Per-Intent Vector Search - STARTED [{}]", 
                    java.time.LocalDateTime.now().format(formatter));
            log.info("   Searching docTypes: {}", docTypes);
            log.info("   Filters: cluster={}, table={}, keyspace={}, topK={}", 
                    searchCluster, searchTable, searchKeyspace, searchTopK);
            
            List<RagQueryResponse.SourceDocument> allResults = new ArrayList<>();
            
            for (String docType : docTypes) {
                IntentEmbedding intentEmbedding = intentEmbeddings.get(docType);
                String subType = docTypeToSubType.get(docType);
                
                if (intentEmbedding == null) {
                    log.warn("   Skipping {} - no embedding generated", docType);
                    continue;
                }
                
                log.info("   Searching: source_type={}, doc_sub_type={}", docType, subType);
                
                long typeSearchStart = System.currentTimeMillis();
                List<RagQueryResponse.SourceDocument> typeResults = searchByDocType(
                    intentEmbedding.embeddingStr, docType, subType, searchCluster, searchTable, searchKeyspace, searchTopK, daysBack
                );
                long typeSearchDuration = System.currentTimeMillis() - typeSearchStart;
                allResults.addAll(typeResults);
                log.info("   Found {} documents for source_type={}, doc_sub_type={} ({}ms)", 
                        typeResults.size(), docType, subType, typeSearchDuration);
                
                // Log per-intent similarity for telemetry
                if (!typeResults.isEmpty()) {
                    Double maxSimilarity = typeResults.stream()
                        .map(doc -> doc.getSimilarityScore())
                        .filter(score -> score != null)
                        .max(Double::compare)
                        .orElse(0.0);
                    log.info("   [{}] Max similarity: {}", docType, String.format("%.3f", maxSimilarity));
                }
            }
            
            // Step 3: Apply per-doc-type similarity threshold filter
            // Each document uses its own doc_sub_type threshold for filtering
            List<RagQueryResponse.SourceDocument> filteredResults = new ArrayList<>();
            Map<String, Integer> thresholdStats = new HashMap<>();  // Track filtering by threshold
            
            for (RagQueryResponse.SourceDocument doc : allResults) {
                // Get threshold for this document's doc_sub_type
                String docSubType = doc.getDocSubType();
                Double threshold = docSubType != null 
                    ? queryRewriteService.getSimilarityThreshold(docSubType, defaultSimilarityThreshold)
                    : defaultSimilarityThreshold;
                
                if (doc.getSimilarityScore() != null && doc.getSimilarityScore() >= threshold) {
                    filteredResults.add(doc);
                    thresholdStats.put(threshold.toString(), thresholdStats.getOrDefault(threshold.toString(), 0) + 1);
                    // Log both similarity and distance for debugging safety
                    Double distance = doc.getSimilarityScore() != null ? 1.0 - doc.getSimilarityScore() : null;
                    log.debug("Document passed: doc_sub_type={}, similarity={}, distance={}, threshold={}", 
                            docSubType, doc.getSimilarityScore(), distance, threshold);
                } else {
                    Double distance = doc.getSimilarityScore() != null ? 1.0 - doc.getSimilarityScore() : null;
                    log.info("Document filtered out: doc_sub_type={}, similarity {} < threshold {}, distance={}", 
                            docSubType, doc.getSimilarityScore(), threshold, distance);
                }
            }
            
            // Log threshold statistics
            // formatter already defined above
            if (!thresholdStats.isEmpty()) {
                log.info("✅ Step 6️⃣: Vector Search - COMPLETED [{}]", 
                        java.time.LocalDateTime.now().format(formatter));
                log.info("   Retrieved {} documents, {} passed similarity filtering", 
                        allResults.size(), filteredResults.size());
                log.info("   Thresholds used: {}", thresholdStats);
                if (!filteredResults.isEmpty()) {
                    Double topDistance = filteredResults.get(0).getSimilarityScore() != null 
                        ? 1.0 - filteredResults.get(0).getSimilarityScore() : null;
                    log.info("   Top document: doc_sub_type={}, similarity={}, distance={}, source={}", 
                            filteredResults.get(0).getDocSubType(),
                            filteredResults.get(0).getSimilarityScore(),
                            topDistance,
                            filteredResults.get(0).getSourceName());
                }
            } else {
                log.info("✅ Step 6️⃣: Vector Search - COMPLETED [{}]", 
                        java.time.LocalDateTime.now().format(formatter));
                log.info("   Retrieved {} documents, {} passed similarity threshold (>= {})", 
                        allResults.size(), filteredResults.size(), defaultSimilarityThreshold);
            }
            log.info("═══════════════════════════════════════════════════════════════");
            
            // Step 4: Sort by similarity (descending) and limit
            filteredResults.sort((a, b) -> {
                Double simA = a.getSimilarityScore() != null ? a.getSimilarityScore() : 0.0;
                Double simB = b.getSimilarityScore() != null ? b.getSimilarityScore() : 0.0;
                return simB.compareTo(simA);
            });
            
            // Limit to maxTopK total results
            if (filteredResults.size() > maxTopK) {
                filteredResults = filteredResults.subList(0, maxTopK);
            }
            
            return filteredResults;
            
        } catch (Exception e) {
            log.error("Error in vector search: {}", e.getMessage(), e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Search for documents of a specific type.
     * 
     * @param embeddingStr Query embedding as PostgreSQL vector string
     * @param docType source_type value (METADATA, LINEAGE, etc.)
     * @param docSubType doc_sub_type value (schema_metadata, business_metadata, etc.) - ✅ GAP 2 FIX
     * @param clusterName Cluster name filter - ✅ GAP 1 FIX
     * @param tableName Table name filter
     * @param keyspace Keyspace filter
     * @param topK Number of results
     */
    private List<RagQueryResponse.SourceDocument> searchByDocType(
            String embeddingStr,
            String docType,
            String docSubType,
            String clusterName,
            String tableName,
            String keyspace,
            Integer topK,
            Integer daysBack) {
        
        try {
            // ✅ Production-grade date filtering: Compute dates from daysBack
            LocalDate[] dateRange = resolveDateRange(daysBack);
            LocalDate startDate = dateRange[0];
            LocalDate endDate = dateRange[1];
            
            log.info("SQL Parameters: clusterName={}, tableName={}, keyspace={}, startDate={}, endDate={}, docSubType={}, sourceType={}", 
                clusterName, tableName, keyspace, startDate, endDate, docSubType, docType);
            
            List<Object[]> results = repository.findSimilarDocumentsBySourceType(
                embeddingStr, clusterName, docType, docSubType, tableName, keyspace, 
                startDate, endDate, topK
            );
            
            return results.stream()
                .map(this::mapToSourceDocument)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error searching docType {}: {}", docType, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Map database row to SourceDocument.
     * Updated row order: id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
     *                    keyspace, table_name, domain, sub_domain, event_date, start_ts, end_ts, time_window, content, metadata,
     *                    embedding, created_at, similarity
     */
    private RagQueryResponse.SourceDocument mapToSourceDocument(Object[] row) {
        // SQL already returns similarity: 1 - (embedding <=> ...) as similarity
        // Similarity is now at index 19 (after all columns including start_ts and end_ts)
        Double similarity = null;
        if (row.length > 19 && row[19] != null) {
            Number simValue = (Number) row[19];
            similarity = simValue.doubleValue(); // SQL already returns similarity, not distance
        }
        
        return RagQueryResponse.SourceDocument.builder()
            .sourceType((String) row[2])      // source_type at index 2
            .docSubType((String) row[3])       // doc_sub_type at index 3 (for per-doc-type threshold)
            .component((String) row[5])       // component at index 5
            .sourceName((String) row[6])       // source_name at index 6
            .content((String) row[15])         // content at index 15 (moved after start_ts, end_ts, time_window)
            .metadata(parseMetadata(row[16]))  // metadata at index 16
            .eventDate(row[11] != null ? ((java.sql.Date) row[11]).toLocalDate() : null)  // event_date at index 11
            .similarityScore(similarity)
            .build();
    }
    
    /**
     * Format embedding list to PostgreSQL vector string.
     */
    private String formatEmbedding(List<Double> embedding) {
        return "[" + embedding.stream()
            .map(d -> String.format("%.6f", d))
            .collect(Collectors.joining(",")) + "]";
    }
    
    /**
     * ✅ Production-grade date computation: Resolve start and end dates from daysBack.
     * Validates daysBack and applies defaults.
     * 
     * @param daysBack Number of days to look back (default: 180, max: 3650)
     * @return Array with [startDate, endDate]
     */
    private LocalDate[] resolveDateRange(Integer daysBack) {
        // Validate daysBack
        if (daysBack != null && daysBack > 3650) {
            log.warn("daysBack {} exceeds maximum (3650), using 180 days", daysBack);
            daysBack = 180;
        }
        if (daysBack != null && daysBack <= 0) {
            log.warn("daysBack {} is invalid, using 180 days", daysBack);
            daysBack = 180;
        }
        
        // Default: 180 days (production best practice)
        if (daysBack == null) {
            daysBack = 180;
        }
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);
        
        log.info("🔵 VECTOR SEARCH DATE FILTER: daysBack={}, fromDate={}, toDate={}", 
                daysBack, startDate, endDate);
        
        return new LocalDate[]{startDate, endDate};
    }
    
    /**
     * Parse time range string to LocalDate array (legacy method for backward compatibility).
     */
    private LocalDate[] parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            return new LocalDate[]{null, null};
        }
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        
        switch (timeRange.toLowerCase()) {
            case "1h":
            case "24h":
                startDate = endDate.minusDays(1);
                break;
            case "7d":
                startDate = endDate.minusDays(7);
                break;
            case "30d":
                startDate = endDate.minusDays(30);
                break;
            default:
                startDate = null;
        }
        
        return new LocalDate[]{startDate, endDate};
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object metadata) {
        if (metadata == null) {
            return new HashMap<>();
        }
        if (metadata instanceof Map) {
            return (Map<String, Object>) metadata;
        }
        return new HashMap<>();
    }
    
    /**
     * Internal class to hold per-intent embedding data.
     */
    private static class IntentEmbedding {
        final String rewrittenQuery;
        final String embeddingStr;
        final String docSubType;
        
        IntentEmbedding(String rewrittenQuery, String embeddingStr, String docSubType) {
            this.rewrittenQuery = rewrittenQuery;
            this.embeddingStr = embeddingStr;
            this.docSubType = docSubType;
        }
    }
    
    /**
     * Call Phi-4 RAG API with structured prompt.
     * The structured prompt already contains system prompt + question + context.
     */
    public String callPhi4(String structuredPrompt, Integer maxTokens, Double temperature) {
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            log.info("🔵 Step 9️⃣: Phi-4 LLM Generation - STARTED [{}]", 
                    java.time.LocalDateTime.now().format(formatter));
            log.info("   Max tokens: {}, Temperature: {}", 
                    maxTokens != null ? maxTokens : 100, 
                    temperature != null ? temperature : 0.3);
            log.info("   Prompt length: {} characters", structuredPrompt.length());
            
            // The structured prompt from PromptBuilderService already contains everything.
            // We need to extract just the question for the query parameter.
            // For simplicity, we'll use the first line after "User Question:" as the query.
            String query = extractQuestionFromPrompt(structuredPrompt);
            String context = structuredPrompt; // Use full prompt as context
            
            long llmStart = System.currentTimeMillis();
            String answer = phi4Client.generateRagAnswer(
                query,
                context,
                maxTokens != null ? maxTokens : 100,
                temperature != null ? temperature : 0.3
            );
            long llmDuration = System.currentTimeMillis() - llmStart;
            
            log.info("✅ Step 9️⃣: Phi-4 LLM Generation - COMPLETED [{}] (Duration: {}ms)", 
                    java.time.LocalDateTime.now().format(formatter), llmDuration);
            log.info("   Answer length: {} characters", answer.length());
            log.info("   Answer preview: {}", answer.substring(0, Math.min(200, answer.length())));
            log.info("═══════════════════════════════════════════════════════════════");
            
            return answer;
        } catch (Exception e) {
            log.error("❌ Step 9️⃣: Phi-4 LLM Generation - ERROR [{}]: {}", 
                    java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")), 
                    e.getMessage(), e);
            throw new RuntimeException("Phi-4 API call failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract question from structured prompt.
     */
    private String extractQuestionFromPrompt(String prompt) {
        // Look for "User Question: " line
        String[] lines = prompt.split("\n");
        for (String line : lines) {
            if (line.startsWith("User Question:")) {
                return line.substring("User Question:".length()).trim();
            }
        }
        // Fallback: return first 100 chars
        return prompt.length() > 100 ? prompt.substring(0, 100) : prompt;
    }
}

