# Database Setup: rag_llm_optimized

This guide explains how to set up the new `rag_llm_optimized` database with HNSW index for 384-dim embeddings.

## Overview

- **Database Name**: `rag_llm_optimized`
- **Schema File**: `sql/02_create_schema_hnsw_384.sql`
- **Embedding Dimension**: 384 (all-MiniLM-L6-v2)
- **Index Type**: HNSW (Hierarchical Navigable Small World)

## Setup Steps

### Option 1: Using the Setup Script (Recommended)

```bash
cd mvp/sql
./setup_rag_llm_optimized.sh
```

The script will:
1. Create the `rag_llm_optimized` database
2. Run the HNSW schema creation script
3. Create all necessary indexes

### Option 2: Manual Setup

```bash
# Step 1: Connect to YugabyteDB
psql -h localhost -p 5433 -U yugabyte -d postgres

# Step 2: Create the database
CREATE DATABASE rag_llm_optimized;

# Step 3: Connect to the new database
\c rag_llm_optimized

# Step 4: Run the schema script
\i sql/02_create_schema_hnsw_384.sql
```

## Indexes Created

The schema creates the following indexes for optimal query performance:

### Vector Index
- **`idx_rag_embedding_hnsw`**: HNSW index on `embedding` column (vector cosine similarity)
  - Parameters: `m = 16`, `ef_construction = 64`
  - Used for: Fast vector similarity search

### Filtering Indexes
- **`idx_rag_cluster`**: On `cluster_name` (multi-cluster support)
- **`idx_rag_source_type`**: On `source_type` (METADATA, LINEAGE, etc.)
- **`idx_rag_doc_sub_type`**: On `doc_sub_type` (12 canonical types)
- **`idx_rag_entity_type`**: On `entity_type` (table, kafka_topic, etc.)
- **`idx_rag_component`**: On `component` (Cassandra, Kafka, Spark, DataAPI)
- **`idx_rag_event_date`**: On `event_date` (time-based filtering)
- **`idx_rag_start_ts`**: On `start_ts` (timestamp range filtering)
- **`idx_rag_end_ts`**: On `end_ts` (timestamp range filtering)
- **`idx_rag_cluster_keyspace_table`**: Composite on `(cluster_name, keyspace, table_name)`
- **`idx_rag_keyspace_table`**: Composite on `(keyspace, table_name)`

## API Configuration

The Spring Boot application has been updated to use the new database:

**File**: `backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:rag_llm_optimized}
```

You can override the database name using the `DB_NAME` environment variable if needed.

## Repository Query Index Usage

The repository queries are designed to leverage these indexes:

1. **Vector Search**: Uses `idx_rag_embedding_hnsw` for similarity search
2. **Cluster Filtering**: Uses `idx_rag_cluster` or `idx_rag_cluster_keyspace_table`
3. **Source Type Filtering**: Uses `idx_rag_source_type`
4. **Doc Sub Type Filtering**: Uses `idx_rag_doc_sub_type`
5. **Table/Keyspace Filtering**: Uses `idx_rag_keyspace_table` or `idx_rag_cluster_keyspace_table`
6. **Time Filtering**: Uses `idx_rag_event_date`, `idx_rag_start_ts`, `idx_rag_end_ts`

## Verification

After setup, verify the database and schema:

```sql
-- Connect to the new database
\c rag_llm_optimized

-- Check if table exists
SELECT COUNT(*) FROM rag_documents;

-- Check indexes
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'rag_documents';

-- Verify HNSW index
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE indexname = 'idx_rag_embedding_hnsw';
```

## Next Steps

1. **Load Data**: Use the embedding generation script to load your 12 canonical documents
2. **Restart Backend**: Restart your Spring Boot application to connect to the new database
3. **Test Queries**: Test vector search queries to verify performance

## Notes

- The HNSW index is optimized for 384-dimensional vectors (all-MiniLM-L6-v2)
- All filtering indexes support fast WHERE clause filtering
- The composite indexes support common query patterns (cluster + keyspace + table)
- `start_ts` and `end_ts` columns are included in SELECT queries for future time-range filtering

