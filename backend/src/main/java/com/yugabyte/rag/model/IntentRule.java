package com.yugabyte.rag.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Represents an intent rule from rag-intents.json.
 * Maps keywords to document types for intent detection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentRule {
    
    @JsonProperty("intent_name")
    private String intentName;
    
    @JsonProperty("doc_type")
    private String docType;
    
    @JsonProperty("keywords")
    private List<String> keywords;
    
    @JsonProperty("time_window_days")
    private Integer timeWindowDays;  // Optional: for time-scoped intents (logs_daily, metrics_weekly, etc.)
}

