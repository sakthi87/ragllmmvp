# 12 Canonical Documents Guide

## Overview

This guide documents the 12 canonical document types for the RAG system, following the production-ready architecture where each Cassandra table generates ~15-20 atomic, intention-aware documents.

## Document Structure

Each document follows this structure:
- **source_type**: High-level category (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
- **doc_sub_type**: Fine-grained type (one of 12 canonical types)
- **content**: LLM-friendly human-readable text
- **metadata**: Structured JSONB with programmatic fields

## The 12 Canonical Types

### Group A: Metadata (Static Documents)

#### 1. business_metadata
**Purpose:** Answer WHO owns the data, WHAT domain it belongs to
- Business owner, technical owner, data steward
- Domain, sub-domain classification
- Environment, creation date

**Example Query:** "Who owns dda_transactions?"

#### 2. schema_metadata
**Purpose:** Answer HOW data is structured
- Primary key, clustering keys
- Column definitions and types
- Default TTL
- CQL CREATE statement

**Example Query:** "What is the schema of dda_transactions?"

#### 3. storage_configuration
**Purpose:** Answer WHY performance behaves a certain way
- Compaction strategy (TWCS, LCS, etc.)
- GC grace seconds
- Compression settings
- Bloom filter configuration
- Caching strategy

**Example Query:** "What compaction strategy does dda_transactions use?"

#### 4. table_statistics
**Purpose:** Answer HOW MUCH data exists
- Size on disk (GB)
- Estimated row count
- Partition count
- SSTable count per node

**Example Query:** "How many rows are in dda_transactions?"

#### 5. data_lifecycle
**Purpose:** Answer HOW LONG data lives
- Default TTL
- Archive frequency and location
- PII columns
- Purge policies

**Example Query:** "What is the TTL for dda_transactions?"

### Group B: Lineage (Static Documents)

#### 6. lineage_kafka
**Purpose:** Answer WHERE data comes from (Kafka)
- Kafka topic name
- Producers
- Schema registry subject
- Throughput metrics

**Example Query:** "Which Kafka topic feeds dda_transactions?"

#### 7. lineage_spark
**Purpose:** Answer HOW data is processed
- Spark job name
- Processing mode (streaming, batch)
- Executor count
- Checkpoint path
- Latency metrics

**Example Query:** "Which Spark job processes dda_transactions?"

#### 8. lineage_dataapi
**Purpose:** Answer WHO consumes the data
- API name
- Endpoints
- SLA requirements
- Partition key usage

**Example Query:** "Which API reads from dda_transactions?"

### Group C: Logs (Rolling, Summary Only)

#### 9. logs_daily
**Purpose:** Answer WHAT broke in the last 24h
- Error counts (timeouts, exceptions, OOMs)
- Component-specific failures
- Time windows
- Impact assessment

**Example Query:** "Were there any failures yesterday?"

#### 10. logs_weekly
**Purpose:** Answer WHAT trends exist over 7 days
- Peak error counts
- Compaction spikes
- OOM event days
- Kafka lag trends

**Example Query:** "What were the log trends this week?"

### Group D: Metrics (Rolling, Summary Only)

#### 11. metrics_daily
**Purpose:** Answer WHEN & HOW BADLY (last 24h)
- Read/write latency (avg, p95, p99)
- Throughput (req/sec)
- Tombstone ratio
- Compaction throughput

**Example Query:** "What was the latency today?"

#### 12. metrics_weekly
**Purpose:** Answer WHEN & HOW BADLY (7-day trends)
- Average daily writes
- Read/write ratio
- Latency trends (week-over-week)
- Compaction backlog changes

**Example Query:** "How has performance changed this week?"

## Loading Documents

### Using Python Script

```bash
cd mvp/scripts
python3 load_canonical_documents.py
```

**Requirements:**
- Phi-4 embedding API running on `http://localhost:8083/api/embed`
- YugabyteDB accessible with credentials in environment variables
- JSON files in `mvp/data/` directory

**Environment Variables:**
```bash
export PHI4_EMBED_URL="http://localhost:8083/api/embed"
export DB_HOST="localhost"
export DB_PORT="5433"
export DB_NAME="postgres"
export DB_USER="yugabyte"
export DB_PASSWORD="yugabyte"
```

### Manual Loading

1. Generate embedding for each document's `content` field using Phi-4 API
2. Insert using parameterized SQL:

```sql
INSERT INTO rag_documents
(cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
 keyspace, table_name, domain, sub_domain, event_date, time_window, 
 content, metadata, embedding)
VALUES 
(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s::vector);
```

## Intent Detection Mapping

The Spring Boot `IntentDetectionService` automatically routes queries to appropriate document types:

| User Question Keywords | Detected Types |
|------------------------|----------------|
| "schema", "primary key", "columns" | METADATA → schema_metadata |
| "domain", "owner", "business" | METADATA → business_metadata |
| "compaction", "tombstone", "storage" | METADATA → storage_configuration |
| "table size", "row count", "statistics" | METADATA → table_statistics |
| "ttl", "retention", "lifecycle" | METADATA → data_lifecycle |
| "kafka topic", "kafka" | LINEAGE → lineage_kafka |
| "spark job", "spark" | LINEAGE → lineage_spark |
| "api", "endpoint" | LINEAGE → lineage_dataapi |
| "today", "yesterday" + "error", "failure" | LOG_SUMMARY → logs_daily |
| "this week" + "error", "failure" | LOG_SUMMARY → logs_weekly |
| "today", "yesterday" + "latency", "slow" | METRIC_SUMMARY → metrics_daily |
| "this week" + "latency", "performance" | METRIC_SUMMARY → metrics_weekly |
| "why", "root cause", "what caused" | All types (RCA query) |

## Scaling to 500 Tables

For each of 500 tables, generate:
- 5 metadata documents (business, schema, storage, statistics, lifecycle)
- 3 lineage documents (kafka, spark, dataapi)
- 4 rolling documents (logs_daily, logs_weekly, metrics_daily, metrics_weekly)

**Total:** ~12 documents per table × 500 tables = **~6,000 documents**

This is manageable for vector search and prevents vector DB explosion.

## File Structure

```
mvp/data/
├── 01_business_metadata.json
├── 02_schema_metadata.json
├── 03_storage_configuration.json
├── 04_table_statistics.json
├── 05_data_lifecycle.json
├── 06_lineage_kafka.json
├── 07_lineage_spark.json
├── 08_lineage_dataapi.json
├── 09_logs_daily.json
├── 10_logs_weekly.json
├── 11_metrics_daily.json
└── 12_metrics_weekly.json
```

## Next Steps

1. ✅ Schema updated with `doc_sub_type NOT NULL`
2. ✅ 12 JSON documents created for `dda_transactions`
3. ✅ Python loading script created
4. ✅ Spring Boot code updated for intent detection
5. ⏳ Load documents into YugabyteDB
6. ⏳ Test queries with each document type
7. ⏳ Scale to additional tables

