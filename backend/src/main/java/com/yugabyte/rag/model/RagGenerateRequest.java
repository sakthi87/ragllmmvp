package com.yugabyte.rag.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagGenerateRequest {
    private String query;
    private String context;
    private Integer maxTokens;
    private Double temperature;
    private Integer topK;      // ✅ Sampling parameter: top_k (default: 50)
    private Double topP;        // ✅ Sampling parameter: top_p (default: 0.95)
}

