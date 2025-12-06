package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request model for /api/rag/ask endpoint.
 */
@Data
public class AskRequest {
    @NotBlank(message = "Question is required")
    private String question;
    
    private String table;
    private String keyspace;
    private Integer topK;
    private Double temperature;
    private Integer maxTokens;
}

