# RAG MVP - Yugabyte PGVector Implementation

Complete setup for RAG (Retrieval Augmented Generation) MVP using:
- **YugabyteDB** with PGVector extension
- **Phi-4 Q4 model** for embeddings and text generation
- **Vector similarity search** for document retrieval

## Scope

- **Keyspace:** `transaction_keyspace`
- **Table:** `dda_transactions`
- **Pipeline:** `Kafka → Spark → Cassandra → Data API (Read)`

## Folder Structure

```
mvp/
├── sql/
│   └── 01_create_schema.sql          # Yugabyte PGVector schema
├── data/
│   ├── metadata.json                  # Table metadata
│   ├── lineage.json                  # Pipeline lineage
│   └── logs_metrics_7days.json       # 7 days of logs & metrics
├── scripts/
│   ├── generate_embeddings.py        # Generate embeddings and load data
│   └── test_rag_query.py             # Test RAG queries
├── config/
│   └── config.env.example            # Configuration template
├── test_questions.md                  # Test questions for MVP
└── README.md                          # This file
```

## Prerequisites

1. **YugabyteDB** running with PGVector extension
2. **Phi-4 Q4 API** running (Docker container on port 8082)
3. **Python 3.8+** with required packages:
   - `psycopg2-binary`
   - `requests`

## Setup Instructions

### 1. Install Python Dependencies

```bash
pip install psycopg2-binary requests
```

### 2. Configure Environment Variables

Copy the example config and update:

```bash
cp config/config.env.example config/config.env
# Edit config.env with your settings
```

Or set environment variables:

```bash
export DB_HOST=localhost
export DB_PORT=5433
export DB_NAME=yugabyte
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
export EMBED_API_URL=http://localhost:8082/api/embed
export GENERATE_API_URL=http://localhost:8082/api/generate
export RAG_API_URL=http://localhost:8082/api/rag
```

### 3. Create Database Schema

Connect to Yugabyte and run:

```bash
psql -h localhost -p 5433 -U yugabyte -d yugabyte -f sql/01_create_schema.sql
```

Or from Python:

```python
import psycopg2
with open('sql/01_create_schema.sql', 'r') as f:
    sql = f.read()
conn = psycopg2.connect(host='localhost', port=5433, user='yugabyte', password='yugabyte', dbname='yugabyte')
cur = conn.cursor()
cur.execute(sql)
conn.commit()
```

### 4. Generate Embeddings and Load Data

Run the bulk loader script:

```bash
cd mvp
python scripts/generate_embeddings.py
```

This will:
- Load metadata document
- Load lineage document
- Load 7 days of logs and metrics (21 documents)
- Generate embeddings using Phi-4 API
- Insert all documents into Yugabyte

**Total: 23 documents** (1 metadata + 1 lineage + 21 logs/metrics)

### 5. Test RAG Queries

Test a query:

```bash
python scripts/test_rag_query.py "What is the schema of dda_transactions?"
```

Or test RCA questions:

```bash
python scripts/test_rag_query.py "Why was dda_transactions delayed yesterday?"
```

## Data Overview

### Document Types

1. **METADATA** (1 document)
   - Table schema information
   - TTL, primary keys, data ownership

2. **LINEAGE** (1 document)
   - Full pipeline flow
   - Kafka → Spark → Cassandra → Data API

3. **LOG_SUMMARY** (7 documents - one per day)
   - Spark job execution logs
   - Data API logs
   - Error counts, runtime metrics

4. **METRIC_SUMMARY** (14 documents - 2 per day)
   - Cassandra write latency
   - Kafka consumer lag
   - Performance metrics

### Time Range

- **Last 7 days:** 2025-11-24 to 2025-11-30
- **Yesterday (worst day):** 2025-11-30
  - 32 Spark OutOfMemoryError events
  - Cassandra latency: 820ms avg, 1.4s peak
  - Kafka lag: 14,800 (peak)
  - API latency: 640ms

## Vector Search Query

The retrieval uses cosine similarity:

```sql
SELECT source_type, component, content, metadata
FROM rag_documents
WHERE table_name = 'dda_transactions'
ORDER BY embedding <=> '[query_vector]'
LIMIT 6;
```

## Test Questions

See `test_questions.md` for complete list of test questions covering:
- Metadata queries
- Lineage queries
- Log queries
- Metric queries
- **RCA (Root Cause Analysis) queries** - The killer demo!

## Troubleshooting

### Connection Issues

- Verify Yugabyte is running: `psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "SELECT 1"`
- Check Phi-4 API: `curl http://localhost:8082/health`

### Embedding API Issues

- Ensure Phi-4 container is running
- Check API logs: `docker logs phi4-rag-api-q4`
- Verify embedding dimension is 384

### Database Issues

- Verify PGVector extension: `CREATE EXTENSION IF NOT EXISTS vector;`
- Check table exists: `SELECT COUNT(*) FROM rag_documents;`

## Next Steps

1. ✅ **Full Spring Boot `/api/rag/query` service**
2. ✅ **Batch Loader for daily logs/metrics**
3. ✅ **React Chat UI connected to Spring**
4. ✅ **Automated Daily Summarization Job**

## Notes for Restricted Environments

- All environment variables for SSL/offline mode are set in Dockerfile
- Embedding model is pre-downloaded in Docker image
- No external network calls needed at runtime
- Works completely offline after initial setup

