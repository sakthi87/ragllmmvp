package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {
    
    @NotBlank(message = "Question is required")
    private String question;
    
    private String keyspace;
    
    private String table;
    
    private String timeRange;  // "1h", "24h", "7d", "30d"
    
    private Integer topK;  // Number of documents to retrieve (default: 6)
    
    private Double temperature;  // LLM temperature (default: 0.3)
    
    private Integer maxTokens;  // Max tokens in response (default: 200)
}

