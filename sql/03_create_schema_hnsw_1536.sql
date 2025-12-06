-- Yugabyte PGVector Schema for RAG MVP (HNSW with 1536-dim embeddings)
-- Embedding Model: text-embedding-3-large (1536 dimensions)
-- Index Type: HNSW (Hierarchical Navigable Small World)
-- Keyspace: transaction_keyspace
-- Table: dda_transactions
-- Pipeline: Kafka → Spark → Cassandra → Data API

-- 1) Enable vector extension (run once)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) Create rag_documents table (production-ready with HNSW)
-- Production-Grade with Multi-Cluster Support and 12 Canonical Document Types
-- Future-ready for higher-dimensional embeddings (text-embedding-3-large)
CREATE TABLE IF NOT EXISTS rag_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ✅ Multi-cluster isolation (GAP 1 FIX)
    cluster_name TEXT NOT NULL,         -- e.g. "cassandra-prod-1"

    -- ✅ Intent routing (2-level typing - GAP 2 FIX)
    source_type TEXT NOT NULL,          -- high-level: METADATA|LINEAGE|LOG_SUMMARY|METRIC_SUMMARY
    doc_sub_type TEXT NOT NULL,         -- one of the 12 fine-grained types:
                                        -- business_metadata, schema_metadata, storage_configuration, table_statistics, data_lifecycle,
                                        -- lineage_kafka, lineage_spark, lineage_dataapi,
                                        -- logs_daily, logs_weekly, metrics_daily, metrics_weekly

    -- ✅ Entity identification (GAP 3 FIX)
    entity_type TEXT,                   -- e.g. table | kafka_topic | spark_job | dataapi
    component TEXT,                     -- Cassandra | Kafka | Spark | DataAPI
    source_name TEXT,                   -- e.g. transaction_keyspace.dda_transactions or kafka.dda_txn_topic

    -- ✅ Cassandra scope
    keyspace TEXT,                      -- Cassandra keyspace
    table_name TEXT,                    -- table name

    -- ✅ Business ownership
    domain TEXT,                        -- business domain, e.g. Retail Banking
    sub_domain TEXT,                    -- e.g. Digital Deposit Accounts

    -- ✅ RCA time filtering
    event_date DATE,                    -- for logs/metrics/events
    start_ts TIMESTAMP,                 -- start timestamp for time windows
    end_ts TIMESTAMP,                  -- end timestamp for time windows
    time_window TEXT,                   -- e.g. "2025-11-28T10:00Z/2025-11-28T10:05Z" or "last_24h", "last_7d"

    -- ✅ Core knowledge
    content TEXT NOT NULL,              -- LLM-friendly human-readable content
    metadata JSONB,                     -- structured fields (pk, ttl, sizes, counters)

    -- ✅ Vector search (1536-dim for text-embedding-3-large)
    embedding vector(1536),             -- 1536 dimensions for text-embedding-3-large embedding model

    created_at TIMESTAMP DEFAULT now()
);

-- 3) Vector HNSW index (optimized for 1536-dim embeddings)
-- HNSW with increased parameters for higher-dimensional vectors
-- Higher m and ef_construction for better recall with 1536-dim embeddings
CREATE INDEX IF NOT EXISTS idx_rag_embedding_hnsw
  ON rag_documents USING hnsw (embedding vector_cosine_ops)
  WITH (m = 32, ef_construction = 128);  -- Increased for higher-dim embeddings

-- 4) Filtering indexes for fast WHERE filtering
CREATE INDEX IF NOT EXISTS idx_rag_cluster ON rag_documents(cluster_name);
CREATE INDEX IF NOT EXISTS idx_rag_source_type ON rag_documents(source_type);
CREATE INDEX IF NOT EXISTS idx_rag_doc_sub_type ON rag_documents(doc_sub_type);
CREATE INDEX IF NOT EXISTS idx_rag_entity_type ON rag_documents(entity_type);
CREATE INDEX IF NOT EXISTS idx_rag_component ON rag_documents(component);
CREATE INDEX IF NOT EXISTS idx_rag_event_date ON rag_documents(event_date);
CREATE INDEX IF NOT EXISTS idx_rag_cluster_keyspace_table ON rag_documents(cluster_name, keyspace, table_name);

-- 5) Composite index for common query patterns
CREATE INDEX IF NOT EXISTS idx_rag_keyspace_table ON rag_documents(keyspace, table_name);

-- Success message
SELECT 'Schema with 1536-dim HNSW embedding created successfully!' as status;

-- Notes:
-- HNSW Parameters:
-- - m = 32: Number of links per node (higher = more accurate, slower indexing)
-- - ef_construction = 128: Quality of graph construction (higher = more accurate)
-- For 1536-dim embeddings, these increased parameters provide better recall
-- Filtering indexes remain the same; they handle WHERE clauses efficiently

