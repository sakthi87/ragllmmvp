# Step-by-Step Logging & Progress Tracking Guide

## ✅ Implementation Summary

All 11 steps in the RAG pipeline now include **detailed logging with timestamps** for easy analysis and debugging.

---

## 📍 Where to Find Logs

### 1. **Console Logs (Real-time)**
- **Location:** Terminal/console where Spring Boot is running
- **Format:** Includes timestamps, request IDs, step numbers, and durations
- **Use:** Real-time monitoring during testing

### 2. **File Logs (Persistent)**
- **Location:** `logs/rag-api.log` (configurable via `LOG_FILE` environment variable)
- **Format:** Same as console, but persisted to disk
- **Use:** Historical analysis, debugging after the fact
- **Rotation:** 10MB max size, 30 days retention

### 3. **UI Debug Mode (Optional)**
- **Location:** React UI (when "Show Debug Info" is enabled)
- **Format:** Shows detected intents and retrieved documents
- **Use:** Quick visual validation during testing

### 4. **Detailed API Endpoint (JSON Response)**
- **Endpoint:** `POST /api/rag/ask-detailed`
- **Format:** JSON with step-by-step progress, timings, inputs/outputs
- **Use:** Programmatic analysis, automated testing, documentation

---

## 📊 Log Format & Structure

### Request ID
Each request gets a unique 8-character ID: `[REQUEST-abc12345]`

### Timestamp Format
All logs include timestamps: `[2025-12-05 18:30:45.123]`

### Step Format
```
🔵 [REQUEST-abc12345] Step 3️⃣: Intent Detection - STARTED [2025-12-05 18:30:45.123]
   Detected source_types: [METADATA]
✅ [REQUEST-abc12345] Step 3️⃣: Intent Detection - COMPLETED [2025-12-05 18:30:45.456] (Duration: 333ms)
```

---

## 🔍 Example Log Output for "What is the schema of dda_transactions?"

```
═══════════════════════════════════════════════════════════════
🔵 [REQUEST-abc12345] Step 1️⃣: User Question Received [2025-12-05 18:30:45.000]
   Question: 'What is the schema of dda_transactions?'
   Table: dda_transactions, Keyspace: transaction_keyspace, TopK: 6

🔵 [REQUEST-abc12345] Step 3️⃣: Intent Detection - STARTED [2025-12-05 18:30:45.001]
✅ [REQUEST-abc12345] Step 3️⃣: Intent Detection - COMPLETED [2025-12-05 18:30:45.333] (Duration: 332ms)
   Detected source_types: [METADATA]
   Detected doc_sub_type: schema_metadata

🔵 [REQUEST-abc12345] Step 4️⃣: Query Rewriting - Will be logged by VectorSearchService [2025-12-05 18:30:45.334]

═══════════════════════════════════════════════════════════════
🔵 Step 4️⃣: Query Rewriting - STARTED [2025-12-05 18:30:45.335]
   Original: 'What is the schema of dda_transactions?'
   Rewritten: 'Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns'
   Template used: doc_sub_type=schema_metadata
✅ Step 4️⃣: Query Rewriting - COMPLETED [2025-12-05 18:30:45.336] (Duration: 1ms)

🔵 Step 5️⃣: Embedding Generation - STARTED [2025-12-05 18:30:45.337]
   Calling embedding API: Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key...
✅ Step 5️⃣: Embedding Generation - COMPLETED [2025-12-05 18:30:46.500] (Duration: 1163ms)
   Embedding dimension: 384

🔵 Step 6️⃣: Vector Search - STARTED [2025-12-05 18:30:46.501]
   Searching docTypes: [METADATA]
   Filters: cluster=null, table=dda_transactions, keyspace=transaction_keyspace, topK=6
   Searching: source_type=METADATA, doc_sub_type=schema_metadata
   Found 1 documents for source_type=METADATA, doc_sub_type=schema_metadata (45ms)
✅ Step 6️⃣: Vector Search - COMPLETED [2025-12-05 18:30:46.546] (Duration: 45ms)
   Retrieved 1 documents, 1 passed similarity filtering
   Thresholds used: {0.75=1}
   Top document: doc_sub_type=schema_metadata, similarity=0.89, source=transaction_keyspace.dda_transactions
═══════════════════════════════════════════════════════════════

✅ [REQUEST-abc12345] Step 7️⃣: Candidate Selection - COMPLETED [2025-12-05 18:30:46.547] (included in Step 5-6)

🔵 [REQUEST-abc12345] Step 8️⃣: Prompt Construction - STARTED [2025-12-05 18:30:46.548]
✅ [REQUEST-abc12345] Step 8️⃣: Prompt Construction - COMPLETED [2025-12-05 18:30:46.550] (Duration: 2ms)
   Prompt length: 1250 characters
   Documents grouped by type: [METADATA]

🔵 [REQUEST-abc12345] Step 9️⃣: Phi-4 LLM Generation - STARTED [2025-12-05 18:30:46.551]
   Calling Phi-4 API: maxTokens=100, temperature=0.3
   Prompt length: 1250 characters
✅ [REQUEST-abc12345] Step 9️⃣: Phi-4 LLM Generation - COMPLETED [2025-12-05 18:35:12.800] (Duration: 286249ms)
   Answer length: 245 characters
   Answer preview: The Cassandra table transaction_keyspace.dda_transactions has primary key transaction_id, clustering key txn_timestamp DESC, and columns: transaction_id (UUID), account_id (UUID), txn_timestamp (TIMESTAMP), amount (DECIMAL), txn_type (TEXT), status (TEXT), merchant_id (UUID). Default TTL is 90 days.
═══════════════════════════════════════════════════════════════

✅ [REQUEST-abc12345] Step 🔟-1️⃣1️⃣: Response Ready - COMPLETED [2025-12-05 18:35:12.801]
═══════════════════════════════════════════════════════════════
✅ [REQUEST-abc12345] TOTAL REQUEST TIME: 286801ms [Started: 2025-12-05 18:30:45.000, Completed: 2025-12-05 18:35:12.801]
   Breakdown: Intent=332ms, VectorSearch=45ms, Prompt=2ms, LLM=286249ms
═══════════════════════════════════════════════════════════════
```

---

## 📋 What Gets Logged at Each Step

### Step 1️⃣: User Question Received
- ✅ Timestamp
- ✅ Question text
- ✅ Table, keyspace, topK parameters
- ✅ Request ID

### Step 3️⃣: Intent Detection
- ✅ Start timestamp
- ✅ Detected source_types
- ✅ Detected doc_sub_type
- ✅ Completion timestamp
- ✅ Duration

### Step 4️⃣: Query Rewriting
- ✅ Start timestamp
- ✅ Original question
- ✅ Rewritten query
- ✅ Template used (doc_sub_type)
- ✅ Completion timestamp
- ✅ Duration

### Step 5️⃣: Embedding Generation
- ✅ Start timestamp
- ✅ Query preview (first 100 chars)
- ✅ Completion timestamp
- ✅ Duration
- ✅ Embedding dimension

### Step 6️⃣: Vector Search
- ✅ Start timestamp
- ✅ DocTypes being searched
- ✅ Filters (cluster, table, keyspace, topK)
- ✅ Per-docType search results
- ✅ Threshold statistics
- ✅ Top document details
- ✅ Completion timestamp
- ✅ Duration

### Step 7️⃣: Candidate Selection
- ✅ Completion timestamp
- ✅ Note that it's included in Step 6

### Step 8️⃣: Prompt Construction
- ✅ Start timestamp
- ✅ Number of documents
- ✅ Completion timestamp
- ✅ Duration
- ✅ Prompt length
- ✅ Documents grouped by type

### Step 9️⃣: Phi-4 LLM Generation
- ✅ Start timestamp
- ✅ Max tokens, temperature
- ✅ Prompt length
- ✅ Completion timestamp
- ✅ Duration
- ✅ Answer length
- ✅ Answer preview

### Step 🔟-1️⃣1️⃣: Response Ready
- ✅ Completion timestamp
- ✅ Total request time
- ✅ Start and end timestamps
- ✅ Breakdown by step

---

## 🎯 How to Analyze Logs

### 1. **Find All Requests for a Question**
```bash
grep "What is the schema" logs/rag-api.log
```

### 2. **Find Slow Requests (> 5 minutes)**
```bash
grep "TOTAL REQUEST TIME" logs/rag-api.log | awk -F' ' '$NF > 300000 {print}'
```

### 3. **Analyze Step Durations**
```bash
grep "Duration:" logs/rag-api.log | grep "Step 9"
```

### 4. **Find Failed Requests**
```bash
grep "ERROR" logs/rag-api.log
```

### 5. **Track Request by ID**
```bash
grep "REQUEST-abc12345" logs/rag-api.log
```

---

## 🔧 Configuration

### Log File Location
Set via environment variable:
```bash
export LOG_FILE=/path/to/custom/logs/rag-api.log
```

Or in `application.yml`:
```yaml
logging:
  file:
    name: ${LOG_FILE:logs/rag-api.log}
```

### Log Level
Currently set to `INFO` for detailed step tracking. To see even more details:
```yaml
logging:
  level:
    com.yugabyte.rag: DEBUG
```

---

## 📊 Using the Detailed API Endpoint

### Request
```bash
curl -X POST http://localhost:8080/api/rag/ask-detailed \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the schema of dda_transactions?",
    "table": "dda_transactions",
    "keyspace": "transaction_keyspace",
    "topK": 6
  }'
```

### Response
```json
{
  "answer": "The Cassandra table...",
  "steps": [
    {
      "stepNumber": 1,
      "stepName": "User Question Received",
      "status": "COMPLETED",
      "durationMs": 0,
      "input": {
        "question": "What is the schema of dda_transactions?",
        "table": "dda_transactions"
      },
      "output": {
        "requestId": "abc12345"
      }
    },
    {
      "stepNumber": 3,
      "stepName": "Intent Detection",
      "status": "COMPLETED",
      "durationMs": 332,
      "input": {
        "question": "What is the schema of dda_transactions?"
      },
      "output": {
        "source_types": ["METADATA"],
        "doc_sub_type": "schema_metadata"
      }
    }
    // ... more steps
  ],
  "summary": {
    "total_duration_ms": 286801,
    "intent_detection_ms": 332,
    "vector_search_ms": 45,
    "prompt_building_ms": 2,
    "llm_generation_ms": 286249,
    "documents_retrieved": 1,
    "request_id": "abc12345"
  }
}
```

---

## ✅ Benefits

1. **Easy Debugging:** See exactly where time is spent
2. **Performance Analysis:** Identify bottlenecks (usually LLM generation)
3. **Request Tracking:** Follow a request through all steps using request ID
4. **Historical Analysis:** Logs persist for 30 days
5. **Validation:** Verify each step produces expected output
6. **Documentation:** Logs serve as execution documentation

---

## 🎯 Next Steps for Testing

1. **Start Spring Boot** and watch console logs
2. **Ask a question** from React UI
3. **Check logs** for step-by-step progress
4. **Use `/ask-detailed` endpoint** for JSON-formatted progress
5. **Analyze log file** for historical requests

**All steps now include timestamps and durations for easy analysis!**

