package com.yugabyte.rag.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yugabyte.rag.model.IntentRule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads intent configuration from rag-intents.json at startup.
 * This provides machine-readable, version-controlled intent rules.
 */
@Component
@Slf4j
@Getter
public class IntentConfigLoader {
    
    private List<IntentRule> rules = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void load() {
        try {
            InputStream is = getClass().getResourceAsStream("/rag-intents.json");
            if (is == null) {
                log.error("❌ rag-intents.json not found in classpath resources!");
                log.warn("⚠️  Falling back to hardcoded intent detection");
                return;
            }
            
            JsonNode root = objectMapper.readTree(is);
            JsonNode intentsNode = root.get("intents");
            
            if (intentsNode == null || !intentsNode.isArray()) {
                log.error("❌ Invalid rag-intents.json format: 'intents' array not found");
                return;
            }
            
            rules = objectMapper.convertValue(
                intentsNode,
                new TypeReference<List<IntentRule>>() {}
            );
            
            log.info("✅ Loaded {} intent rules from rag-intents.json", rules.size());
            log.debug("Intent rules: {}", rules);
            
        } catch (Exception e) {
            log.error("❌ Error loading rag-intents.json: {}", e.getMessage(), e);
            log.warn("⚠️  Falling back to hardcoded intent detection");
            rules = new ArrayList<>();  // Empty list triggers fallback
        }
    }
    
    /**
     * Check if JSON-based intents are loaded successfully.
     */
    public boolean isLoaded() {
        return rules != null && !rules.isEmpty();
    }
}

