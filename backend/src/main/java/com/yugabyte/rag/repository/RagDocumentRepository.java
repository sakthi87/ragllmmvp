package com.yugabyte.rag.repository;

import com.yugabyte.rag.model.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
    
    @Query(value = """
        SELECT id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
               keyspace, table_name, domain, sub_domain, event_date, start_ts, end_ts, time_window, content, metadata,
               embedding, created_at,
               1 - (embedding <=> CAST(:embedding AS vector)) as similarity
        FROM rag_documents
        WHERE (:clusterName IS NULL OR cluster_name = :clusterName)
          AND (:tableName IS NULL OR table_name = :tableName)
          AND (:keyspace IS NULL OR keyspace = :keyspace)
          AND event_date >= :startDate
          AND event_date <= :endDate
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findSimilarDocuments(
        @Param("embedding") String embedding,
        @Param("clusterName") String clusterName,
        @Param("tableName") String tableName,
        @Param("keyspace") String keyspace,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("topK") Integer topK
    );
    
    List<RagDocument> findByTableNameAndKeyspace(String tableName, String keyspace);
    
    @Query(value = """
        SELECT id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
               keyspace, table_name, domain, sub_domain, event_date, start_ts, end_ts, time_window, content, metadata,
               embedding, created_at,
               1 - (embedding <=> CAST(:embedding AS vector)) as similarity
        FROM rag_documents
        WHERE (:clusterName IS NULL OR cluster_name = :clusterName)
          AND source_type = :sourceType
          AND (:docSubType IS NULL OR doc_sub_type = :docSubType)
          AND (:tableName IS NULL OR table_name = :tableName)
          AND (:keyspace IS NULL OR keyspace = :keyspace)
          AND event_date >= :startDate
          AND event_date <= :endDate
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findSimilarDocumentsBySourceType(
        @Param("embedding") String embedding,
        @Param("clusterName") String clusterName,
        @Param("sourceType") String sourceType,
        @Param("docSubType") String docSubType,
        @Param("tableName") String tableName,
        @Param("keyspace") String keyspace,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("topK") Integer topK
    );
}

