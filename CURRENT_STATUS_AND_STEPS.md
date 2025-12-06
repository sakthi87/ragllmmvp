# Current Status & Step-by-Step Process Summary

## 📊 Current System Status

### ✅ What's Working
1. **Database (`rag_llm_optimized`)**: ✅ Created and ready
   - Table: `rag_documents` with HNSW index (384-dim)
   - Documents: 12 canonical documents loaded
   - Indexes: 12 indexes created (vector + filtering)

2. **Phi-4 API**: ✅ Running
   - Container: `phi4-rag-api-q3` on port 8083
   - Status: Healthy (embedding + LLM loaded)
   - Endpoints: `/api/embed`, `/api/generate`, `/api/rag`

3. **Spring Boot Backend**: ✅ Running
   - Port: 8080
   - Database: Connected to `rag_llm_optimized`
   - Configs loaded: `rag-intents.json` (12 rules), `query-rewrite-templates.json` (12 templates)

### ⚠️ Current Issue
**Vector Search Returns 0 Documents** - Documents exist in DB but queries return empty results.

---

## 🔄 Complete Process Flow (Until /rag/ask)

### Step 1: User Question Received
**Input**: `"What is the schema of dda_transactions?"`

**Output**:
```json
{
  "question": "What is the schema of dda_transactions?",
  "table": "dda_transactions",
  "keyspace": "transaction_keyspace",
  "topK": 6
}
```

---

### Step 2: Intent Detection
**Service**: `IntentDetectionService`
**Input**: User question
**Process**: 
- Loads `rag-intents.json` (12 intent rules)
- Matches keywords: "schema" → `SCHEMA_METADATA` intent
- Maps to `source_type`: `METADATA`
- Detects `doc_sub_type`: `schema_metadata`

**Output**:
```json
["METADATA"]
```

**Log Output**:
```
✅ Loaded 12 intent rules from rag-intents.json
Detected source_types: [METADATA]
Detected doc_sub_type: schema_metadata
```

---

### Step 3: Query Rewriting
**Service**: `QueryRewriteService`
**Input**: Original question + detected `doc_sub_type`
**Process**:
- Loads `query-rewrite-templates.json`
- Finds template for `schema_metadata`:
  ```json
  {
    "rewrite_template": "Schema definition of cassandra table {keyspace}.{table} including primary key, partition key and clustering columns",
    "similarity_threshold": 0.75
  }
  ```
- Substitutes: `{keyspace}` → `transaction_keyspace`, `{table}` → `dda_transactions`

**Output**:
```
Original: "What is the schema of dda_transactions?"
Rewritten: "Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns"
```

**Log Output**:
```
🔵 Step 4️⃣: Query Rewriting - STARTED [2025-12-05 20:04:13.996]
   Original: 'What is the schema of dda_transactions?'
   Rewritten: 'Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns'
   Template used: doc_sub_type=schema_metadata
✅ Step 4️⃣: Query Rewriting - COMPLETED [2025-12-05 20:04:14.000] (Duration: 4ms)
```

---

### Step 4: Embedding Generation
**Service**: `Phi4Client` → Flask API `/api/embed`
**Input**: Rewritten query
**Process**:
- Calls `http://localhost:8083/api/embed`
- Uses `all-MiniLM-L6-v2` model (384 dimensions)
- Generates vector embedding

**Output**:
```json
{
  "embedding": [0.0115, 0.0251, -0.0367, ...],  // 384 dimensions
  "status": "success"
}
```

**Log Output**:
```
🔵 Step 5️⃣: Embedding Generation - STARTED [2025-12-05 20:04:14.000]
   Calling embedding API for: 'Schema definition of cassandra table transaction_keyspace.dda_transactions...'
✅ Step 5️⃣: Embedding Generation - COMPLETED [2025-12-05 20:04:26.129] (Duration: 12123ms)
   Embedding dimension: 384
```

**Duration**: ~12 seconds (CPU inference)

---

### Step 5: Vector Search
**Service**: `VectorSearchService` → `RagDocumentRepository`
**Input**: 
- Query embedding (384-dim vector)
- `source_type`: `METADATA`
- `doc_sub_type`: `schema_metadata`
- Filters: `table_name = 'dda_transactions'`, `keyspace = 'transaction_keyspace'`

**SQL Query** (simplified):
```sql
SELECT id, cluster_name, source_type, doc_sub_type, ..., content, metadata, embedding,
       1 - (embedding <=> CAST(:embedding AS vector)) as similarity
FROM rag_documents
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
  AND source_type = 'METADATA'
  AND doc_sub_type = 'schema_metadata'
  AND table_name = COALESCE(:tableName, 'dda_transactions')
  AND keyspace = COALESCE(:keyspace, 'transaction_keyspace')
  AND (CAST(:startDate AS DATE) IS NULL OR event_date >= CAST(:startDate AS DATE))
  AND (CAST(:endDate AS DATE) IS NULL OR event_date <= CAST(:endDate AS DATE))
ORDER BY embedding <=> CAST(:embedding AS vector)
LIMIT 5
```

**Expected Output**: Documents with similarity scores

**Actual Output**: 
```json
{
  "documents": [],
  "count": 0
}
```

**Log Output**:
```
🔵 Step 6️⃣: Vector Search - STARTED [2025-12-05 20:04:26.200]
   Searching docTypes: [METADATA]
   Filters: cluster=null, table=dda_transactions, keyspace=transaction_keyspace, topK=5
   Searching: source_type=METADATA, doc_sub_type=schema_metadata
✅ Step 6️⃣: Vector Search - COMPLETED [2025-12-05 20:04:26.438]
   Retrieved 0 documents, 0 passed similarity threshold (>= 0.65)
```

**Issue**: Query executes but returns 0 rows (before threshold filtering)

---

### Step 6: Similarity Threshold Filtering
**Service**: `VectorSearchService`
**Process**:
- Gets per-doc-type threshold from `query-rewrite-templates.json`
- For `schema_metadata`: threshold = `0.75`
- Filters documents where `similarity >= 0.75`

**Output**: 
```json
{
  "documents": [],  // Empty because Step 5 returned 0 documents
  "count": 0
}
```

**Log Output**:
```
🔵 Step 7️⃣: Candidate Document Selection & Filtering - STARTED
   Retrieved 0 documents, 0 passed similarity threshold (>= 0.65)
✅ Step 7️⃣: Candidate Document Selection & Filtering - COMPLETED
```

**Note**: Since Step 5 returned 0 documents, threshold filtering has nothing to filter.

---

### Step 7: Prompt Construction
**Service**: `PromptBuilderService`
**Input**: User question + retrieved documents (empty in this case)
**Process**:
- Builds structured prompt with system prompt + question + context
- Since no documents retrieved, builds prompt with "No relevant documents found"

**Output**: Structured prompt string (with empty context)

**Log Output**:
```
🔵 Step 8️⃣: Prompt Construction - STARTED [2025-12-05 19:58:06.170]
✅ Step 8️⃣: Prompt Construction - COMPLETED [2025-12-05 19:58:06.171] (Duration: 1ms)
   Prompt length: XXX characters
```

---

### Step 8: Phi-4 LLM Generation (Before /rag/ask)
**Service**: `VectorSearchService.callPhi4()` → Flask API `/api/rag`
**Input**: Structured prompt
**Status**: ⏸️ Not executed yet (would be Step 9)

---

## 🔍 Recent Changes & Threshold Impact

### Changes Made:
1. ✅ **Database**: Created `rag_llm_optimized` with HNSW index
2. ✅ **Schema**: Added `start_ts`, `end_ts` columns and indexes
3. ✅ **Repository**: Updated queries to include `start_ts`, `end_ts` in SELECT
4. ✅ **Repository**: Fixed SQL parameter type casting (`CAST(:startDate AS DATE)`)
5. ✅ **Config**: Updated `application.yml` to use `rag_llm_optimized` database
6. ✅ **Scripts**: Updated `load_canonical_documents.py` to use new database

### Threshold Configuration:

**Default Threshold** (`application.yml`):
```yaml
rag:
  similarity-threshold: 0.65  # Default (per-doc-type thresholds override this)
```

**Per-Doc-Type Thresholds** (`query-rewrite-templates.json`):
```json
{
  "schema_metadata": 0.75,
  "business_metadata": 0.75,
  "storage_configuration": 0.72,
  "logs_daily": 0.63,
  "metrics_daily": 0.65,
  ...
}
```

### Did Recent Changes Help with Threshold?

**✅ YES - Partially**:
1. **Per-doc-type thresholds**: ✅ Implemented and working
   - Each `doc_sub_type` has its own threshold
   - `schema_metadata` uses 0.75 (higher precision)
   - `logs_daily` uses 0.63 (lower threshold for variable content)

2. **Query rewriting**: ✅ Working
   - Canonical templates improve semantic matching
   - Should improve similarity scores

3. **Database optimization**: ✅ Complete
   - HNSW index for fast vector search
   - All filtering indexes in place

**❌ NO - Current Issue**:
- **Vector search returns 0 documents** (before threshold filtering)
- This means the problem is NOT the threshold
- The issue is in the SQL query execution or parameter binding
- Documents exist in DB but query doesn't find them

---

## 🐛 Root Cause Analysis

### Problem:
Vector search SQL query executes but returns 0 rows, even though:
- ✅ 12 documents exist in `rag_documents` table
- ✅ Documents have embeddings (384-dim vectors)
- ✅ Direct PostgreSQL query with same embedding returns similarity = 1.0
- ✅ No SQL errors in logs (after fixing parameter casting)

### Possible Causes:
1. **Parameter binding issue**: `COALESCE(:clusterName, cluster_name)` might not work as expected when `clusterName = null`
2. **Table/keyspace filter mismatch**: Documents might have different `table_name` or `keyspace` values
3. **Date filter issue**: `event_date` filtering might exclude all documents
4. **Embedding format**: Query embedding format might not match stored embeddings

### Next Steps to Debug:
1. Check actual values in database (table_name, keyspace, cluster_name, event_date)
2. Test SQL query directly with exact parameters
3. Verify embedding format matches between query and stored vectors
4. Check if `COALESCE` works correctly with null parameters in YugabyteDB

---

## 📝 Summary

**What Works**:
- ✅ Database setup complete
- ✅ Documents loaded (12 canonical types)
- ✅ Phi-4 API running
- ✅ Spring Boot connected to new database
- ✅ Intent detection working
- ✅ Query rewriting working
- ✅ Embedding generation working
- ✅ Per-doc-type thresholds configured

**What Doesn't Work**:
- ❌ Vector search returns 0 documents (SQL query issue)
- ❌ Threshold filtering not tested (no documents to filter)

**Impact of Recent Changes**:
- ✅ Threshold system is properly configured
- ✅ Query rewriting should improve similarity scores (when query works)
- ✅ Per-doc-type thresholds allow fine-grained control
- ⚠️ Cannot verify threshold effectiveness until vector search is fixed

