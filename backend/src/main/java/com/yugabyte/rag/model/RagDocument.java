package com.yugabyte.rag.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "rag_documents", indexes = {
    @Index(name = "idx_rag_cluster", columnList = "cluster_name"),
    @Index(name = "idx_rag_keyspace_table", columnList = "keyspace,table_name"),
    @Index(name = "idx_rag_source_type", columnList = "source_type"),
    @Index(name = "idx_rag_doc_sub_type", columnList = "doc_sub_type"),
    @Index(name = "idx_rag_entity_type", columnList = "entity_type"),
    @Index(name = "idx_rag_component", columnList = "component"),
    @Index(name = "idx_rag_event_date", columnList = "event_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    // ✅ Multi-cluster isolation (GAP 1 FIX)
    @Column(name = "cluster_name", nullable = false)
    private String clusterName;
    
    // ✅ Intent routing (2-level typing - GAP 2 FIX)
    @Column(name = "source_type", nullable = false)
    private String sourceType;  // METADATA | LINEAGE | LOG_SUMMARY | METRIC_SUMMARY
    
    @Column(name = "doc_sub_type", nullable = false)
    private String docSubType;  // 12 canonical types:
                                // business_metadata, schema_metadata, storage_configuration, table_statistics, data_lifecycle
                                // lineage_kafka, lineage_spark, lineage_dataapi
                                // logs_daily, logs_weekly, metrics_daily, metrics_weekly
    
    // ✅ Entity identification (GAP 3 FIX)
    @Column(name = "entity_type")
    private String entityType;  // table | kafka_topic | spark_job | api | batch_job
    
    @Column(name = "component")
    private String component;
    
    @Column(name = "source_name")
    private String sourceName;
    
    @Column(name = "keyspace")
    private String keyspace;
    
    @Column(name = "table_name")
    private String tableName;
    
    @Column(name = "domain")
    private String domain;
    
    @Column(name = "sub_domain")
    private String subDomain;
    
    @Column(name = "event_date")
    private LocalDate eventDate;
    
    @Column(name = "time_window")
    private String timeWindow;
    
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private String embedding;  // Stored as string, converted for queries (nullable for inserts before embedding generation)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

