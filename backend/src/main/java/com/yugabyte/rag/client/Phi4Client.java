package com.yugabyte.rag.client;

import com.yugabyte.rag.model.EmbedRequest;
import com.yugabyte.rag.model.EmbedResponse;
import com.yugabyte.rag.model.GenerateRequest;
import com.yugabyte.rag.model.GenerateResponse;
import com.yugabyte.rag.model.RagGenerateRequest;
import com.yugabyte.rag.model.RagGenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class Phi4Client {
    
    private final WebClient webClient;
    
    @Value("${phi4.embed-url}")
    private String embedUrl;
    
    @Value("${phi4.generate-url}")
    private String generateUrl;
    
    @Value("${phi4.rag-url}")
    private String ragUrl;
    
    @Value("${phi4.timeout:300000}")
    private long timeout;
    
    public Phi4Client() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    public List<Double> generateEmbedding(String text) {
        try {
            log.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));
            
            EmbedRequest request = new EmbedRequest(text);
            
            EmbedResponse response = webClient.post()
                    .uri(embedUrl)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbedResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            if (response == null || response.getEmbedding() == null) {
                throw new RuntimeException("Failed to generate embedding: null response");
            }
            
            log.debug("Generated embedding with {} dimensions", response.getEmbedding().size());
            return response.getEmbedding();
            
        } catch (Exception e) {
            log.error("Error generating embedding: {}", e.getMessage());
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    public String generateText(String prompt, Integer maxTokens, Double temperature) {
        try {
            log.debug("Generating text with maxTokens: {}, temperature: {}", maxTokens, temperature);
            
            GenerateRequest request = new GenerateRequest(prompt, maxTokens, temperature);
            
            GenerateResponse response = webClient.post()
                    .uri(generateUrl)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GenerateResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            if (response == null || response.getText() == null) {
                throw new RuntimeException("Failed to generate text: null response");
            }
            
            return response.getText();
            
        } catch (Exception e) {
            log.error("Error generating text: {}", e.getMessage());
            throw new RuntimeException("Failed to generate text: " + e.getMessage(), e);
        }
    }
    
    public String generateRagAnswer(String query, String context, Integer maxTokens, Double temperature) {
        return generateRagAnswerWithRetry(query, context, maxTokens, temperature, 2);
    }
    
    /**
     * Generate RAG answer with timeout and retry logic.
     * 
     * @param query User question
     * @param context Full context/prompt
     * @param maxTokens Max tokens to generate
     * @param temperature Temperature for generation
     * @param maxRetries Maximum number of retry attempts
     * @return Generated answer
     */
    public String generateRagAnswerWithRetry(String query, String context, Integer maxTokens, 
                                             Double temperature, int maxRetries) {
        // Per-intent timeout: 60 seconds (much shorter than full request timeout)
        Duration intentTimeout = Duration.ofSeconds(60);
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.warn("Retry attempt {} for RAG answer generation", attempt);
                }
                
                log.debug("Generating RAG answer with context length: {}", context.length());
                
                // ✅ Add sampling parameters: top_k=50, top_p=0.95
                RagGenerateRequest request = new RagGenerateRequest(
                    query, 
                    context, 
                    maxTokens, 
                    temperature,
                    50,    // topK = 50
                    0.95   // topP = 0.95
                );
                
                RagGenerateResponse response = webClient.post()
                        .uri(ragUrl)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(RagGenerateResponse.class)
                        .timeout(intentTimeout) // 60 seconds per intent
                        .block();
                
                if (response == null || response.getText() == null) {
                    if (attempt < maxRetries) {
                        log.warn("Null response on attempt {}, retrying...", attempt + 1);
                        continue;
                    }
                    throw new RuntimeException("Failed to generate RAG answer: null response after " + (attempt + 1) + " attempts");
                }
                
                String answer = response.getText();
                
                // Validate answer is not empty
                if (answer.trim().isEmpty()) {
                    if (attempt < maxRetries) {
                        log.warn("Empty answer on attempt {}, retrying...", attempt + 1);
                        continue;
                    }
                    log.warn("Empty answer after {} attempts", attempt + 1);
                }
                
                return answer;
                
            } catch (Exception e) {
                // Check if it's a timeout exception (WebClient throws reactor timeout)
                if (e.getMessage() != null && (e.getMessage().contains("timeout") || 
                    e.getMessage().contains("Timeout") ||
                    e.getClass().getSimpleName().contains("Timeout"))) {
                    log.warn("Timeout generating RAG answer (attempt {}): {}", attempt + 1, e.getMessage());
                    if (attempt < maxRetries) {
                        continue; // Retry
                    }
                    throw new RuntimeException("RAG answer generation timed out after " + (attempt + 1) + " attempts", e);
                }
                log.error("Error generating RAG answer (attempt {}): {}", attempt + 1, e.getMessage());
                if (attempt < maxRetries) {
                    // Wait a bit before retry
                    try {
                        Thread.sleep(1000 * (attempt + 1)); // Exponential backoff: 1s, 2s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue; // Retry
                }
                throw new RuntimeException("Failed to generate RAG answer after " + (attempt + 1) + " attempts: " + e.getMessage(), e);
            }
        }
        
        // Should not reach here, but just in case
        throw new RuntimeException("Failed to generate RAG answer after all retries");
    }
    
    public boolean checkHealth() {
        try {
            String healthUrl = embedUrl.replace("/api/embed", "/health");
            String response = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null && response.contains("healthy");
        } catch (Exception e) {
            log.warn("Phi-4 health check failed: {}", e.getMessage());
            return false;
        }
    }
}

