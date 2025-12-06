package com.yugabyte.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a detected intent from user question.
 * Contains the doc_type and optional time window information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectedIntent {
    
    /**
     * The doc_sub_type value (e.g., "schema_metadata", "logs_daily")
     */
    private String docType;
    
    /**
     * Time window in days (1 for daily, 7 for weekly, null for static docs)
     */
    private Integer timeWindowDays;
    
    /**
     * The source_type derived from doc_type
     * (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
     */
    private String sourceType;
    
    public DetectedIntent(String docType, Integer timeWindowDays) {
        this.docType = docType;
        this.timeWindowDays = timeWindowDays;
        this.sourceType = deriveSourceType(docType);
    }
    
    /**
     * Derive source_type from doc_type.
     */
    private String deriveSourceType(String docType) {
        if (docType == null) {
            return null;
        }
        
        if (docType.startsWith("business_") || docType.startsWith("schema_") || 
            docType.startsWith("storage_") || docType.startsWith("table_") || 
            docType.startsWith("data_")) {
            return "METADATA";
        }
        
        if (docType.startsWith("lineage_")) {
            return "LINEAGE";
        }
        
        if (docType.startsWith("logs_")) {
            return "LOG_SUMMARY";
        }
        
        if (docType.startsWith("metrics_")) {
            return "METRIC_SUMMARY";
        }
        
        return null;
    }
}

