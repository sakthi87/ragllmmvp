# Local Testing Guide - Full RAG Flow

## ✅ Container Status

**Phi-4 Q3 Container**: Running ✅
- **Container Name**: `phi4-rag-api-q3`
- **Port**: `8083` (mapped to container port 5000)
- **Memory Usage**: ~1.8 GB (within limits)
- **Status**: Healthy and ready

**YugabyteDB**: Running ✅
- **Port**: `5433`
- **Status**: Active

---

## Quick Start Testing

### 1. Test Phi-4 API Endpoints

#### Health Check
```bash
curl http://localhost:8083/health
```
**Expected**: `{"embedding_loaded":true,"llm_loaded":true,"status":"healthy"}`

#### Embedding Test
```bash
curl -X POST http://localhost:8083/api/embed \
  -H "Content-Type: application/json" \
  -d '{"text": "What is the schema of dda_transactions?"}'
```
**Expected**: JSON with `embedding` array (384 dimensions)

#### RAG Generation Test (Small - Fast)
```bash
curl -X POST http://localhost:8083/api/rag \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the schema?",
    "context": "The dda_transactions table has columns: transaction_id, account_id, txn_amount",
    "max_tokens": 50,
    "temperature": 0.3
  }'
```
**Expected**: Answer text (may take 2-5 minutes on CPU)

---

### 2. Test Full RAG Flow with Python Scripts

#### Step 1: Test Simple Retrieval (No LLM)
```bash
cd mvp/scripts
export EMBED_API_URL=http://localhost:8083/api/embed
export DB_NAME=postgres
export DB_HOST=localhost
export DB_PORT=5433

python3 test_rag_simple.py "What is the schema of dda_transactions?"
```
**Expected**: Shows retrieved documents with similarity scores

#### Step 2: Test Full RAG Query (With LLM)
```bash
cd mvp/scripts
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/rag
export DB_NAME=postgres
export DB_HOST=localhost
export DB_PORT=5433

python3 test_rag_query.py "What is the schema of dda_transactions?"
```
**Expected**: 
- Retrieves documents (fast)
- Generates answer (slow - 2-5 minutes)
- Shows latency breakdown

---

### 3. Test Spring Boot Backend (If Running)

#### Start Backend (if not running)
```bash
cd mvp/backend
mvn spring-boot:run
```

#### Test Intent Detection
```bash
curl -X POST http://localhost:8080/api/rag/detect-intent \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the schema of dda_transactions?"}'
```
**Expected**: `["METADATA"]`

#### Test Vector Search
```bash
curl -X POST http://localhost:8080/api/rag/search-vector \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the schema of dda_transactions?",
    "docTypes": ["METADATA"],
    "table": "dda_transactions",
    "topK": 6
  }'
```
**Expected**: Documents array with similarity scores

#### Test Full RAG Query (/ask endpoint)
```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the schema of dda_transactions?",
    "table": "dda_transactions",
    "keyspace": "transaction_keyspace",
    "topK": 6,
    "temperature": 0.3,
    "maxTokens": 50
  }'
```
**Expected**: Answer string (2-5 minutes on CPU)

---

### 4. Test React Frontend (If Running)

#### Start Frontend
```bash
cd mvp/frontend
npm start
```

#### Open Browser
- Navigate to: `http://localhost:3000`
- Enable "Show Debug Info" checkbox
- Ask questions like:
  - "What is the schema of dda_transactions?"
  - "Why was dda_transactions delayed yesterday?"
  - "Which Kafka topic feeds dda_transactions?"

---

## Expected Performance

| Operation | Time | Notes |
|-----------|------|-------|
| Health Check | < 1s | Fast |
| Embedding | 200-500ms | Fast |
| Vector Search | 50-200ms | Fast (Yugabyte) |
| LLM Inference (50 tokens) | 2-5 min | **CPU is slow** |
| LLM Inference (100 tokens) | 4-8 min | **CPU is slow** |
| Full RAG Query | 2-5 min | Mostly LLM time |

---

## Monitoring Commands

### Check Container Status
```bash
docker ps | grep phi4
docker stats phi4-rag-api-q3 --no-stream
```

### Check Logs
```bash
# Recent logs
docker logs phi4-rag-api-q3 --tail 50

# Follow logs in real-time
docker logs phi4-rag-api-q3 -f
```

### Check Memory Usage
```bash
docker stats phi4-rag-api-q3 --format "Memory: {{.MemUsage}} | CPU: {{.CPUPerc}}"
```

### Check System Resources
```bash
# System memory
vm_stat | head -5

# Docker memory
docker stats --no-stream
```

---

## Troubleshooting

### Container Not Responding
```bash
# Restart container
docker restart phi4-rag-api-q3

# Check logs for errors
docker logs phi4-rag-api-q3 --tail 100
```

### Out of Memory
```bash
# Check memory usage
docker stats phi4-rag-api-q3

# If memory is high, reduce max_tokens in requests
# Or restart container to free memory
docker restart phi4-rag-api-q3
```

### Slow Response Times
- **Normal**: CPU inference is slow (2-5 minutes)
- **Solution**: Use smaller `max_tokens` (50 instead of 100)
- **Alternative**: Wait for GPU access for faster inference

### Port Already in Use
```bash
# Check what's using port 8083
lsof -i :8083

# Stop conflicting container
docker stop <container-name>
```

---

## Test Scenarios

### Scenario 1: Schema Query
**Question**: "What is the schema of dda_transactions?"
- **Expected Intent**: `["METADATA"]`
- **Expected doc_sub_type**: `"schema_metadata"`
- **Expected Documents**: 1 METADATA document
- **Expected Answer**: Contains column names, primary key, TTL

### Scenario 2: RCA Query
**Question**: "Why was dda_transactions delayed yesterday?"
- **Expected Intent**: `["LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"]`
- **Expected Documents**: Multiple documents from different types
- **Expected Answer**: Combines info from logs, metrics, and lineage

### Scenario 3: Lineage Query
**Question**: "Which Kafka topic feeds dda_transactions?"
- **Expected Intent**: `["LINEAGE"]`
- **Expected entity_type**: `"kafka_topic"`
- **Expected Answer**: Mentions Kafka topic name

---

## Success Criteria

✅ **Container Running**: `docker ps` shows container as "Up"
✅ **Health Check**: Returns `{"status":"healthy"}`
✅ **Embedding Works**: Returns 384-dimensional vector
✅ **Vector Search Works**: Returns documents from Yugabyte
✅ **RAG Query Works**: Returns answer (even if slow)
✅ **No Memory Errors**: Container doesn't crash
✅ **Logs Clean**: No error messages in logs

---

## Next Steps After Testing

1. ✅ **Verify all endpoints work**
2. ✅ **Test with different question types**
3. ✅ **Monitor memory usage during inference**
4. ✅ **Test with Spring Boot backend**
5. ✅ **Test with React frontend**
6. ✅ **Validate answers are accurate**

---

## Quick Reference

### Container Management
```bash
# Start
docker start phi4-rag-api-q3

# Stop
docker stop phi4-rag-api-q3

# Restart
docker restart phi4-rag-api-q3

# Remove
docker rm -f phi4-rag-api-q3
```

### API Endpoints
- **Health**: `http://localhost:8083/health`
- **Embed**: `http://localhost:8083/api/embed`
- **Generate**: `http://localhost:8083/api/generate`
- **RAG**: `http://localhost:8083/api/rag`

### Environment Variables
```bash
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/rag
export DB_NAME=postgres
export DB_HOST=localhost
export DB_PORT=5433
```

---

**You're all set! Start testing the full RAG flow.** 🚀

