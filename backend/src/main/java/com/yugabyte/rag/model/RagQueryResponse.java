package com.yugabyte.rag.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagQueryResponse {
    
    private String answer;
    
    private Double confidence;  // 0.0 to 1.0
    
    private List<SourceDocument> sources;
    
    private String mode;  // METADATA, LINEAGE, LOGS, METRICS, RCA
    
    private Long retrievalTimeMs;
    
    private Long generationTimeMs;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceDocument {
        private String sourceType;
        private String docSubType;  // Added for per-doc-type similarity threshold filtering
        private String component;
        private String sourceName;
        private String content;
        private Map<String, Object> metadata;
        private LocalDate eventDate;
        private Double similarityScore;
    }
}

