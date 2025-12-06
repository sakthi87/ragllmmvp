package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagIngestRequest {
    
    @NotBlank
    private String clusterName;  // ✅ Multi-cluster isolation (GAP 1 FIX)
    
    @NotBlank
    private String sourceType;  // METADATA | LINEAGE | LOG_SUMMARY | METRIC_SUMMARY
    
    private String docSubType;  // ✅ 2-level typing (GAP 2 FIX): schema_metadata | business_metadata | storage_configuration | data_lifecycle
    
    private String entityType;  // ✅ Entity identification (GAP 3 FIX): table | kafka_topic | spark_job | api | batch_job
    
    @NotBlank
    private String component;  // Kafka | Spark | Cassandra | DataAPI
    
    @NotBlank
    private String sourceName;
    
    @NotBlank
    private String keyspace;
    
    @NotBlank
    private String tableName;
    
    private String domain;
    
    private String subDomain;
    
    private LocalDate eventDate;
    
    private String timeWindow;
    
    @NotBlank
    private String content;
    
    private Map<String, Object> metadata;
}

