# Quick Start Guide - RAG MVP

## Prerequisites Check

```bash
# 1. Check Yugabyte is running
psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "SELECT version();"

# 2. Check Phi-4 API is running
curl http://localhost:8082/health
```

## Step-by-Step Setup

### Step 1: Install Dependencies

```bash
cd mvp
pip install -r requirements.txt
```

### Step 2: Create Database Schema

```bash
psql -h localhost -p 5433 -U yugabyte -d yugabyte -f sql/01_create_schema.sql
```

### Step 3: Set Environment Variables (Optional)

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

### Step 4: Load Data with Embeddings

```bash
python scripts/generate_embeddings.py
```

Expected output:
- Loading metadata document...
- Loading lineage document...
- Loading logs and metrics (7 days)...
- Generating embeddings and loading documents...
- Successfully loaded: 23

### Step 5: Test RAG Query

```bash
# Test metadata question
python scripts/test_rag_query.py "What is the schema of dda_transactions?"

# Test RCA question (killer demo!)
python scripts/test_rag_query.py "Why was dda_transactions delayed yesterday?"
```

## Expected Results

### Metadata Query Example
**Question:** "What is the schema of dda_transactions?"

**Answer:** Should mention:
- transaction_id (primary key)
- account_id, txn_amount, txn_type
- txn_timestamp, branch_id
- TTL: 90 days

### RCA Query Example
**Question:** "Why was dda_transactions delayed yesterday?"

**Answer:** Should mention:
- Spark OutOfMemoryError events (32 events)
- Cassandra write latency increased (820ms avg, 1.4s peak)
- Kafka consumer lag spiked (14,800)
- API latency increased (640ms)
- Time window: 11:40-12:05 UTC

## Troubleshooting

### Issue: "Connection refused" to Phi-4 API
```bash
# Check if container is running
docker ps | grep phi4-rag-api-q4

# Check container logs
docker logs phi4-rag-api-q4
```

### Issue: "Database connection failed"
```bash
# Verify Yugabyte is accessible
psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "SELECT 1"

# Check if PGVector extension is installed
psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### Issue: "Expected 384-dimensional embedding"
- Verify Phi-4 API is using all-MiniLM-L6-v2 model
- Check API response: `curl -X POST http://localhost:8082/api/embed -H "Content-Type: application/json" -d '{"text":"test"}'`

## Next Steps

1. Try all questions from `test_questions.md`
2. Test different temperature settings (0.1, 0.3, 0.7)
3. Adjust `top_k` parameter in retrieval (default: 6)
4. Experiment with different prompt formats

## Files Overview

- `sql/01_create_schema.sql` - Database schema
- `data/metadata.json` - Table metadata (1 doc)
- `data/lineage.json` - Pipeline lineage (1 doc)
- `data/logs_metrics_7days.json` - 7 days of logs/metrics (21 docs)
- `scripts/generate_embeddings.py` - Bulk loader
- `scripts/test_rag_query.py` - Query tester
- `test_questions.md` - All test questions

**Total Documents: 23**

