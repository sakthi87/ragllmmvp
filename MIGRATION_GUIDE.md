# Migration Guide - Schema Updates

## Overview
This guide helps you migrate from the original schema to the production-grade schema with 3 critical gap fixes.

## Migration Steps

### Step 1: Backup Existing Data

```sql
-- Create backup table
CREATE TABLE rag_documents_backup AS SELECT * FROM rag_documents;
```

### Step 2: Add New Columns

```sql
-- Add cluster_name (GAP 1 FIX)
ALTER TABLE rag_documents ADD COLUMN cluster_name TEXT;
UPDATE rag_documents SET cluster_name = 'default-cluster' WHERE cluster_name IS NULL;
ALTER TABLE rag_documents ALTER COLUMN cluster_name SET NOT NULL;

-- Add doc_sub_type (GAP 2 FIX)
ALTER TABLE rag_documents ADD COLUMN doc_sub_type TEXT;

-- Add entity_type (GAP 3 FIX)
ALTER TABLE rag_documents ADD COLUMN entity_type TEXT;
```

### Step 3: Populate New Columns

```sql
-- Set doc_sub_type based on existing source_type and content
UPDATE rag_documents 
SET doc_sub_type = 'schema_metadata'
WHERE source_type = 'METADATA' 
  AND (content ILIKE '%schema%' OR content ILIKE '%primary key%' OR content ILIKE '%columns%');

UPDATE rag_documents 
SET doc_sub_type = 'business_metadata'
WHERE source_type = 'METADATA' 
  AND (content ILIKE '%domain%' OR content ILIKE '%owner%' OR content ILIKE '%pii%');

UPDATE rag_documents 
SET doc_sub_type = 'storage_configuration'
WHERE source_type = 'METADATA' 
  AND (content ILIKE '%tombstone%' OR content ILIKE '%compaction%');

UPDATE rag_documents 
SET doc_sub_type = 'data_lifecycle'
WHERE source_type = 'METADATA' 
  AND (content ILIKE '%ttl%' OR content ILIKE '%retention%' OR content ILIKE '%lifecycle%');

-- Set entity_type based on component and source_name
UPDATE rag_documents 
SET entity_type = 'table'
WHERE component = 'Cassandra' AND entity_type IS NULL;

UPDATE rag_documents 
SET entity_type = 'kafka_topic'
WHERE component = 'Kafka' AND entity_type IS NULL;

UPDATE rag_documents 
SET entity_type = 'spark_job'
WHERE component = 'Spark' AND entity_type IS NULL;

UPDATE rag_documents 
SET entity_type = 'api'
WHERE component = 'DataAPI' AND entity_type IS NULL;
```

### Step 4: Add Indexes

```sql
-- Add new indexes
CREATE INDEX idx_rag_cluster ON rag_documents(cluster_name);
CREATE INDEX idx_rag_doc_sub_type ON rag_documents(doc_sub_type);
CREATE INDEX idx_rag_entity_type ON rag_documents(entity_type);
```

### Step 5: Update Vector Index (Optional - for better performance)

```sql
-- Drop old index
DROP INDEX IF EXISTS idx_rag_embedding_ivf;

-- Create HNSW index (better performance)
CREATE INDEX idx_rag_embedding_hnsw
ON rag_documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- If HNSW fails, use IVFFLAT:
-- CREATE INDEX idx_rag_embedding_ivf
-- ON rag_documents USING ivfflat (embedding vector_cosine_ops)
-- WITH (lists = 100);
```

### Step 6: Verify Migration

```sql
-- Check column counts
SELECT 
    COUNT(*) as total_docs,
    COUNT(DISTINCT cluster_name) as clusters,
    COUNT(DISTINCT doc_sub_type) as sub_types,
    COUNT(DISTINCT entity_type) as entity_types
FROM rag_documents;

-- Verify NOT NULL constraints
SELECT COUNT(*) FROM rag_documents WHERE cluster_name IS NULL;  -- Should be 0
SELECT COUNT(*) FROM rag_documents WHERE source_type IS NULL;   -- Should be 0
SELECT COUNT(*) FROM rag_documents WHERE content IS NULL;       -- Should be 0
SELECT COUNT(*) FROM rag_documents WHERE embedding IS NULL;     -- Should be 0
```

## Rollback Plan

If migration fails, restore from backup:

```sql
-- Drop current table
DROP TABLE rag_documents;

-- Restore backup
ALTER TABLE rag_documents_backup RENAME TO rag_documents;
```

## Post-Migration Checklist

- [ ] All documents have `cluster_name` set
- [ ] METADATA documents have appropriate `doc_sub_type`
- [ ] LINEAGE documents have appropriate `entity_type`
- [ ] All indexes are created
- [ ] Vector index is working
- [ ] Application code is updated to use new fields
- [ ] Test queries work with new schema

## Application Code Updates

After schema migration, ensure:

1. **RagIngestRequest** includes `clusterName`, `docSubType`, `entityType`
2. **VectorSearchService** passes `clusterName` to repository queries
3. **IntentDetectionService** uses `detectDocSubType()` for METADATA queries
4. **Configuration** sets `rag.cluster-filter` if needed

## Example: Ingesting Document with New Fields

```json
{
  "clusterName": "prod-cluster-1",
  "sourceType": "METADATA",
  "docSubType": "schema_metadata",
  "entityType": "table",
  "component": "Cassandra",
  "sourceName": "transaction_keyspace.dda_transactions",
  "keyspace": "transaction_keyspace",
  "tableName": "dda_transactions",
  "domain": "Banking",
  "subDomain": "Retail Transactions",
  "content": "The dda_transactions table stores...",
  "metadata": {
    "ttl_days": 90,
    "primary_key": "transaction_id"
  }
}
```

