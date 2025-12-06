package com.yugabyte.rag.service;

import com.yugabyte.rag.client.Phi4Client;
import com.yugabyte.rag.model.RagDocument;
import com.yugabyte.rag.model.RagIngestRequest;
import com.yugabyte.rag.model.RagQueryRequest;
import com.yugabyte.rag.model.RagQueryResponse;
import com.yugabyte.rag.model.RagSearchRequest;
import com.yugabyte.rag.repository.RagDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {
    
    private final RagDocumentRepository repository;
    private final Phi4Client phi4Client;
    private final PromptEngine promptEngine;
    private final PromptBuilderService promptBuilderService;
    
    @Value("${rag.default-top-k:6}")
    private Integer defaultTopK;
    
    @Value("${rag.max-top-k:10}")
    private Integer maxTopK;
    
    @Value("${rag.keyspace-filter:transaction_keyspace}")
    private String defaultKeyspace;
    
    @Value("${rag.table-filter:dda_transactions}")
    private String defaultTable;
    
    public RagService(RagDocumentRepository repository, Phi4Client phi4Client, 
                     PromptEngine promptEngine, PromptBuilderService promptBuilderService) {
        this.repository = repository;
        this.phi4Client = phi4Client;
        this.promptEngine = promptEngine;
        this.promptBuilderService = promptBuilderService;
    }
    
    /**
     * Build structured prompt using PromptBuilderService.
     * Used by /ask endpoint.
     */
    public String buildStructuredPrompt(String question, List<RagQueryResponse.SourceDocument> documents) {
        return promptBuilderService.buildPrompt(question, documents);
    }
    
    public RagQueryResponse query(RagQueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Generate query embedding
            log.info("Generating embedding for question: {}", request.getQuestion());
            List<Double> queryEmbedding = phi4Client.generateEmbedding(request.getQuestion());
            String embeddingStr = formatEmbedding(queryEmbedding);
            
            long retrievalStart = System.currentTimeMillis();
            
            // Step 2: Retrieve similar documents
            String tableName = request.getTable() != null ? request.getTable() : defaultTable;
            String keyspace = request.getKeyspace() != null ? request.getKeyspace() : defaultKeyspace;
            Integer topK = request.getTopK() != null ? Math.min(request.getTopK(), maxTopK) : defaultTopK;
            
            LocalDate[] dateRange = parseTimeRange(request.getTimeRange());
            
            List<Object[]> results = repository.findSimilarDocuments(
                embeddingStr, null, tableName, keyspace, dateRange[0], dateRange[1], topK
            );
            
            long retrievalTime = System.currentTimeMillis() - retrievalStart;
            
            // Step 3: Build context and sources
            List<RagQueryResponse.SourceDocument> sources = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            
            for (Object[] row : results) {
                // Updated row order: id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
                //                    keyspace, table_name, domain, sub_domain, event_date, time_window, content, metadata,
                //                    embedding, created_at, similarity
                RagQueryResponse.SourceDocument source = RagQueryResponse.SourceDocument.builder()
                    .sourceType((String) row[2])  // source_type at index 2
                    .component((String) row[5])    // component at index 5
                    .sourceName((String) row[6])   // source_name at index 6
                    .content((String) row[13])     // content at index 13
                    .metadata(parseMetadata(row[14]))  // metadata at index 14
                    .eventDate(row[11] != null ? ((java.sql.Date) row[11]).toLocalDate() : null)  // event_date at index 11
                    .similarityScore(row[17] != null ? (1.0 - ((Number) row[17]).doubleValue()) : null)  // similarity at index 17 (convert distance to similarity)
                    .build();
                sources.add(source);
                
                contextBuilder.append(String.format("[%s - %s] %s\n\n", 
                    source.getSourceType(), source.getComponent(), source.getContent()));
            }
            
            String context = contextBuilder.toString();
            log.info("Retrieved {} documents, context length: {}", sources.size(), context.length());
            
            // Step 4: Detect mode and build prompt
            String mode = promptEngine.detectMode(request.getQuestion());
            String prompt = promptEngine.buildPrompt(request.getQuestion(), context, mode);
            
            long generationStart = System.currentTimeMillis();
            
            // Step 5: Generate answer
            // Use smaller maxTokens for CPU-only inference (much slower)
            Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 100;
            Double temperature = request.getTemperature() != null ? request.getTemperature() : 0.3;
            
            String answer = phi4Client.generateRagAnswer(
                request.getQuestion(), context, maxTokens, temperature
            );
            
            long generationTime = System.currentTimeMillis() - generationStart;
            
            // Step 6: Calculate confidence (based on similarity scores)
            Double confidence = calculateConfidence(sources);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Query completed in {}ms (retrieval: {}ms, generation: {}ms)", 
                totalTime, retrievalTime, generationTime);
            
            return RagQueryResponse.builder()
                .answer(answer)
                .confidence(confidence)
                .sources(sources)
                .mode(mode)
                .retrievalTimeMs(retrievalTime)
                .generationTimeMs(generationTime)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing RAG query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process RAG query: " + e.getMessage(), e);
        }
    }
    
    public List<RagQueryResponse.SourceDocument> search(RagSearchRequest request) {
        try {
            List<Double> queryEmbedding = phi4Client.generateEmbedding(request.getQuestion());
            String embeddingStr = formatEmbedding(queryEmbedding);
            
            String tableName = request.getTable() != null ? request.getTable() : defaultTable;
            Integer topK = request.getTopK() != null ? Math.min(request.getTopK(), maxTopK) : defaultTopK;
            
            List<Object[]> results = repository.findSimilarDocuments(
                embeddingStr, null, tableName, defaultKeyspace, null, null, topK
            );
            
            return results.stream().map(row -> {
                // Updated row order: id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
                //                    keyspace, table_name, domain, sub_domain, event_date, time_window, content, metadata,
                //                    embedding, created_at, similarity
                return RagQueryResponse.SourceDocument.builder()
                    .sourceType((String) row[2])  // source_type at index 2
                    .component((String) row[5])    // component at index 5
                    .sourceName((String) row[6])   // source_name at index 6
                    .content((String) row[13])     // content at index 13
                    .metadata(parseMetadata(row[14]))  // metadata at index 14
                    .eventDate(row[11] != null ? ((java.sql.Date) row[11]).toLocalDate() : null)  // event_date at index 11
                    .similarityScore(row[17] != null ? (1.0 - ((Number) row[17]).doubleValue()) : null)  // similarity at index 17
                    .build();
            }).collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error in search: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public RagDocument ingest(RagIngestRequest request) {
        try {
            log.info("Ingesting document: {} - {}", request.getSourceType(), request.getSourceName());
            
            // Generate embedding
            List<Double> embedding = phi4Client.generateEmbedding(request.getContent());
            String embeddingStr = formatEmbedding(embedding);
            
            // Create document
            RagDocument document = new RagDocument();
            document.setId(UUID.randomUUID());
            document.setClusterName(request.getClusterName());  // ✅ GAP 1 FIX
            document.setSourceType(request.getSourceType());
            document.setDocSubType(request.getDocSubType());  // ✅ GAP 2 FIX
            document.setEntityType(request.getEntityType());  // ✅ GAP 3 FIX
            document.setComponent(request.getComponent());
            document.setSourceName(request.getSourceName());
            document.setKeyspace(request.getKeyspace());
            document.setTableName(request.getTableName());
            document.setDomain(request.getDomain());
            document.setSubDomain(request.getSubDomain());
            document.setEventDate(request.getEventDate());
            document.setTimeWindow(request.getTimeWindow());
            document.setContent(request.getContent());
            document.setMetadata(request.getMetadata());
            document.setEmbedding(embeddingStr);
            document.setCreatedAt(LocalDateTime.now());
            
            RagDocument saved = repository.save(document);
            log.info("Successfully ingested document with ID: {}", saved.getId());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Error ingesting document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ingest document: " + e.getMessage(), e);
        }
    }
    
    private String formatEmbedding(List<Double> embedding) {
        return "[" + embedding.stream()
            .map(d -> String.format("%.6f", d))
            .collect(Collectors.joining(",")) + "]";
    }
    
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
    
    private Double calculateConfidence(List<RagQueryResponse.SourceDocument> sources) {
        if (sources.isEmpty()) {
            return 0.0;
        }
        
        double avgSimilarity = sources.stream()
            .filter(s -> s.getSimilarityScore() != null)
            .mapToDouble(RagQueryResponse.SourceDocument::getSimilarityScore)
            .average()
            .orElse(0.0);
        
        // Normalize to 0-1 range (cosine similarity is already 0-1)
        return Math.min(1.0, Math.max(0.0, avgSimilarity));
    }
    
    public boolean checkPhi4Health() {
        return phi4Client.checkHealth();
    }
    
    public boolean checkYugabyteHealth() {
        try {
            repository.count();
            return true;
        } catch (Exception e) {
            log.warn("Yugabyte health check failed: {}", e.getMessage());
            return false;
        }
    }
}

