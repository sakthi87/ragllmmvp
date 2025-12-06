package com.yugabyte.rag.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Represents a query rewrite template from query-rewrite-templates.json.
 * Used to rewrite user questions into canonical, embedding-friendly queries.
 * Includes example questions for intent detection and per-doc-type similarity thresholds.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRewriteTemplate {
    
    @JsonProperty("doc_sub_type")
    private String docSubType;
    
    @JsonProperty("source_type")
    private String sourceType;
    
    @JsonProperty("rewrite_template")
    private String rewriteTemplate;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("example")
    private String example;
    
    @JsonProperty("similarity_threshold")
    private Double similarityThreshold;
    
    @JsonProperty("example_questions")
    private List<String> exampleQuestions;
}

