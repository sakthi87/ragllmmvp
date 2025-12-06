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
}

