package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request model for /api/rag/search-vector endpoint.
 */
@Data
public class SearchVectorRequest {
    @NotBlank(message = "Question is required")
    private String question;
    
    @NotEmpty(message = "Document types are required")
    private List<String> docTypes;
    
    private String table;
    private String keyspace;
    private Integer topK;
    
    // ✅ Production-grade date filtering: daysBack from today (default: 180 days)
    private Integer daysBack;
}

