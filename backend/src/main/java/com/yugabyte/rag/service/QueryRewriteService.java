package com.yugabyte.rag.service;

import com.yugabyte.rag.config.QueryRewriteConfigLoader;
import com.yugabyte.rag.model.QueryRewriteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for rewriting user questions into canonical, embedding-friendly queries.
 * This improves embedding similarity by using structured, domain-specific query templates.
 * 
 * Flow: User Question → Intent Detection → Query Rewriting → Embedding Generation
 */
@Service
@Slf4j
public class QueryRewriteService {
    
    private final QueryRewriteConfigLoader configLoader;
    
    @Value("${rag.keyspace-filter:transaction_keyspace}")
    private String defaultKeyspace;
    
    @Value("${rag.table-filter:dda_transactions}")
    private String defaultTable;
    
    @Autowired
    public QueryRewriteService(QueryRewriteConfigLoader configLoader) {
        this.configLoader = configLoader;
    }
    
    /**
     * Rewrite user question into canonical query for embedding.
     * Uses doc_sub_type-specific templates to improve semantic similarity.
     * 
     * @param userQuestion Original user question
     * @param docSubType Detected doc_sub_type (e.g., "schema_metadata", "logs_daily")
     * @param keyspace Optional keyspace (uses default if null)
     * @param table Optional table name (uses default if null)
     * @return Rewritten canonical query, or original question if no template found
     */
    public String rewriteQuery(String userQuestion, String docSubType, String keyspace, String table) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return userQuestion;
        }
        
        // If no doc_sub_type detected, return original question
        if (docSubType == null || docSubType.trim().isEmpty()) {
            log.debug("No doc_sub_type provided, skipping query rewrite");
            return userQuestion;
        }
        
        // Get rewrite template for this doc_sub_type
        QueryRewriteTemplate template = configLoader.getTemplate(docSubType);
        if (template == null) {
            log.debug("No rewrite template found for doc_sub_type: {}, using original question", docSubType);
            return userQuestion;
        }
        
        // Use provided keyspace/table or defaults
        String searchKeyspace = keyspace != null ? keyspace : defaultKeyspace;
        String searchTable = table != null ? table : defaultTable;
        
        // Replace placeholders in template
        String rewrittenQuery = template.getRewriteTemplate()
            .replace("{keyspace}", searchKeyspace)
            .replace("{table}", searchTable);
        
        log.info("Query rewritten: '{}' → '{}' (doc_sub_type: {})", 
                userQuestion, rewrittenQuery, docSubType);
        
        return rewrittenQuery;
    }
    
    /**
     * Rewrite query for multiple doc_sub_types (for multi-intent queries like RCA).
     * Returns the most appropriate rewrite based on primary intent.
     * 
     * @param userQuestion Original user question
     * @param docSubTypes List of detected doc_sub_types
     * @param keyspace Optional keyspace
     * @param table Optional table name
     * @return Rewritten query (uses first available template)
     */
    public String rewriteQueryMulti(String userQuestion, java.util.List<String> docSubTypes, 
                                     String keyspace, String table) {
        if (docSubTypes == null || docSubTypes.isEmpty()) {
            return userQuestion;
        }
        
        // Try to find a template for any of the detected doc_sub_types
        for (String docSubType : docSubTypes) {
            String rewritten = rewriteQuery(userQuestion, docSubType, keyspace, table);
            if (!rewritten.equals(userQuestion)) {
                return rewritten;  // Return first successful rewrite
            }
        }
        
        return userQuestion;  // Fallback to original
    }
    
    /**
     * Get similarity threshold for a specific doc_sub_type.
     * 
     * @param docSubType The doc_sub_type
     * @param defaultThreshold Default threshold if not configured
     * @return Configured threshold or default
     */
    public Double getSimilarityThreshold(String docSubType, Double defaultThreshold) {
        return configLoader.getSimilarityThreshold(docSubType, defaultThreshold);
    }
}

