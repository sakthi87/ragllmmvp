package com.yugabyte.rag.service;

import com.yugabyte.rag.model.RagQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building structured prompts for Phi-4 LLM.
 * Organizes context by document type and includes system prompt.
 */
@Service
@Slf4j
public class PromptBuilderService {
    
    private static final String SYSTEM_PROMPT = 
        "You are an enterprise data platform assistant.\n" +
        "You must answer only using the provided metadata sections.\n" +
        "If multiple sections are present, you must logically combine them.\n" +
        "If any part of the question cannot be answered from the metadata,\n" +
        "you must explicitly say which part is missing.\n" +
        "CRITICAL: If the answer does not explicitly appear in the metadata context,\n" +
        "respond with: 'I cannot find this information in the current metadata.'\n" +
        "Do not fabricate or infer information that is not present in the provided context.\n" +
        "Be specific and cite relevant details from the context.\n" +
        "Format your answer clearly with proper structure.";
    
    /**
     * Build structured prompt with system prompt, user question, and context organized by doc type.
     * 
     * @param question User's question
     * @param documents Retrieved documents
     * @return Structured prompt string
     */
    public String buildPrompt(String question, List<RagQueryResponse.SourceDocument> documents) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be empty");
        }
        
        if (documents == null || documents.isEmpty()) {
            log.warn("No documents provided for prompt building");
            return buildPromptWithNoContext(question);
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        log.info("🔵 Step 8️⃣: Prompt Construction - STARTED [{}]", LocalDateTime.now().format(formatter));
        log.info("   Building prompt with {} documents", documents.size());
        
        StringBuilder prompt = new StringBuilder();
        
        // Step 1: System Prompt (constant)
        prompt.append(SYSTEM_PROMPT);
        prompt.append("\n\n");
        
        // Step 2: User Question (variable)
        prompt.append("User Question: ");
        prompt.append(question);
        prompt.append("\n\n");
        
        // Step 3: Context organized by document type
        prompt.append("Context:\n");
        prompt.append("=".repeat(80));
        prompt.append("\n\n");
        
        // Group documents by source_type
        Map<String, List<RagQueryResponse.SourceDocument>> docsByType = documents.stream()
            .collect(Collectors.groupingBy(
                doc -> doc.getSourceType() != null ? doc.getSourceType() : "UNKNOWN"
            ));
        
        // Order: METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY
        List<String> typeOrder = Arrays.asList("METADATA", "LINEAGE", "LOG_SUMMARY", "METRIC_SUMMARY");
        
        for (String docType : typeOrder) {
            List<RagQueryResponse.SourceDocument> typeDocs = docsByType.get(docType);
            if (typeDocs != null && !typeDocs.isEmpty()) {
                prompt.append("=== ").append(docType).append(" ===\n");
                
                for (int i = 0; i < typeDocs.size(); i++) {
                    RagQueryResponse.SourceDocument doc = typeDocs.get(i);
                    prompt.append(String.format("[%d] %s - %s", 
                        i + 1, 
                        doc.getComponent() != null ? doc.getComponent() : "Unknown",
                        doc.getSourceName() != null ? doc.getSourceName() : "Unknown"));
                    
                    if (doc.getEventDate() != null) {
                        prompt.append(" (Date: ").append(doc.getEventDate()).append(")");
                    }
                    
                    if (doc.getSimilarityScore() != null) {
                        prompt.append(String.format(" [Relevance: %.1f%%]", doc.getSimilarityScore() * 100));
                    }
                    
                    prompt.append("\n");
                    prompt.append(doc.getContent() != null ? doc.getContent() : "");
                    prompt.append("\n\n");
                }
            }
        }
        
        // Add any remaining document types not in the standard order
        for (Map.Entry<String, List<RagQueryResponse.SourceDocument>> entry : docsByType.entrySet()) {
            if (!typeOrder.contains(entry.getKey())) {
                prompt.append("=== ").append(entry.getKey()).append(" ===\n");
                for (RagQueryResponse.SourceDocument doc : entry.getValue()) {
                    prompt.append(doc.getContent() != null ? doc.getContent() : "");
                    prompt.append("\n\n");
                }
            }
        }
        
        prompt.append("=".repeat(80));
        prompt.append("\n\n");
        prompt.append("Please provide a comprehensive answer based on the context above.\n");
        
        String finalPrompt = prompt.toString();
        // formatter already defined above
        log.info("✅ Step 8️⃣: Prompt Construction - COMPLETED [{}]", LocalDateTime.now().format(formatter));
        log.info("   Prompt length: {} characters", finalPrompt.length());
        log.info("   Documents grouped by type: {}", docsByType.keySet());
        log.debug("   Prompt preview (first 300 chars): {}", 
                finalPrompt.substring(0, Math.min(300, finalPrompt.length())));
        
        return finalPrompt;
    }
    
    /**
     * Build prompt when no context is available.
     */
    private String buildPromptWithNoContext(String question) {
        return SYSTEM_PROMPT + "\n\n" +
               "User Question: " + question + "\n\n" +
               "Context: No relevant documents found in the knowledge base.\n\n" +
               "Please inform the user that no information is available to answer this question.";
    }
    
    /**
     * Build a simplified prompt for direct Phi-4 API calls.
     * This is used when calling /api/rag endpoint directly.
     */
    public String buildSimplePrompt(String question, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT);
        prompt.append("\n\n");
        prompt.append("User Question: ");
        prompt.append(question);
        prompt.append("\n\n");
        prompt.append("Context:\n");
        prompt.append(context);
        prompt.append("\n\n");
        prompt.append("Please provide a comprehensive answer based on the context above.\n");
        
        return prompt.toString();
    }
}

