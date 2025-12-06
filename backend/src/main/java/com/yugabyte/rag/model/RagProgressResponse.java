package com.yugabyte.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response model that includes step-by-step progress for RAG requests.
 * Used for debugging and validation of each step in the RAG pipeline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagProgressResponse {
    
    private String answer;
    
    private List<StepProgress> steps;
    
    private Map<String, Object> summary;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepProgress {
        private Integer stepNumber;
        private String stepName;
        private String description;
        private String status;  // STARTED, COMPLETED, ERROR
        private Long durationMs;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private String error;
    }
}

