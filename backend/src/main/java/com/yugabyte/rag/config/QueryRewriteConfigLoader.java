package com.yugabyte.rag.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yugabyte.rag.model.QueryRewriteTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads query rewrite templates from query-rewrite-templates.json at startup.
 * Provides canonical query rewriting for improved embedding similarity.
 */
@Component
@Slf4j
@Getter
public class QueryRewriteConfigLoader {
    
    private Map<String, QueryRewriteTemplate> templateMap = new HashMap<>();
    private Map<String, Double> similarityThresholdMap = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void load() {
        try {
            InputStream is = getClass().getResourceAsStream("/query-rewrite-templates.json");
            if (is == null) {
                log.error("❌ query-rewrite-templates.json not found in classpath resources!");
                log.warn("⚠️  Query rewriting will be disabled");
                return;
            }
            
            List<QueryRewriteTemplate> templates = objectMapper.readValue(
                is,
                new TypeReference<List<QueryRewriteTemplate>>() {}
            );
            
            // Build maps for fast lookup
            for (QueryRewriteTemplate template : templates) {
                templateMap.put(template.getDocSubType(), template);
                if (template.getSimilarityThreshold() != null) {
                    similarityThresholdMap.put(template.getDocSubType(), template.getSimilarityThreshold());
                }
            }
            
            log.info("✅ Loaded {} query rewrite templates from query-rewrite-templates.json", templates.size());
            log.debug("Templates: {}", templateMap.keySet());
            
        } catch (Exception e) {
            log.error("❌ Error loading query-rewrite-templates.json: {}", e.getMessage(), e);
            log.warn("⚠️  Query rewriting will be disabled");
            templateMap = new HashMap<>();
            similarityThresholdMap = new HashMap<>();
        }
    }
    
    /**
     * Get rewrite template for a doc_sub_type.
     */
    public QueryRewriteTemplate getTemplate(String docSubType) {
        return templateMap.get(docSubType);
    }
    
    /**
     * Get similarity threshold for a doc_sub_type.
     * Returns default threshold if not configured.
     */
    public Double getSimilarityThreshold(String docSubType, Double defaultThreshold) {
        return similarityThresholdMap.getOrDefault(docSubType, defaultThreshold);
    }
    
    /**
     * Check if templates are loaded successfully.
     */
    public boolean isLoaded() {
        return templateMap != null && !templateMap.isEmpty();
    }
}

