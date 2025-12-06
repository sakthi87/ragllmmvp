# Multi-Intent Test Results V1 - Actual vs Expected

## 🧠 User Question (Multi-Intent)

**Question**: "What is the schema of dda_transactions, what errors occurred in the last 24 hours, and is the write latency normal today?"

**Expected Intents**:
1. ✅ Schema (METADATA → schema_metadata)
2. ✅ Errors in last 24 hours (LOG_SUMMARY → logs_daily)
3. ✅ Write latency today (METRIC_SUMMARY → metrics_daily)

---

## ✅ Step-by-Step Actual Results

### Step 1️⃣: User Question
**Status**: ✅ Received

**Input**:
```json
{
  "question": "What is the schema of dda_transactions, what errors occurred in the last 24 hours, and is the write latency normal today?",
  "docTypes": ["METADATA", "LOG_SUMMARY", "METRIC_SUMMARY"],
  "topK": 5,
  "daysBack": 180
}
```

---

### Step 2️⃣: POST to /api/rag/search-vector
**Endpoint**: `POST /api/rag/search-vector`
**Status**: ✅ Executed

---

### Step 3️⃣: Multi-Intent Detection
**Endpoint**: `POST /api/rag/detect-intent`

**Actual Response**:
```json
["METADATA", "METRIC_SUMMARY"]
```

**Expected Response**:
```json
["METADATA", "LOG_SUMMARY", "METRIC_SUMMARY"]
```

**Analysis**:
- ✅ Detected METADATA (schema intent)
- ✅ Detected METRIC_SUMMARY (latency intent)
- ❌ **MISSING**: LOG_SUMMARY (errors intent) - not detected automatically
- ⚠️ **Issue**: Intent detection only found 2 of 3 intents

**Note**: When explicitly passing `["METADATA", "LOG_SUMMARY", "METRIC_SUMMARY"]` in the request, the system processes all three types.

---

### Step 4️⃣: Query Rewriting

**Expected Behavior**: 
- Should create **3 separate rewritten queries** (one per intent):
  - Pipeline A: "Schema definition of cassandra table transaction_keyspace.dda_transactions..."
  - Pipeline B: "Cassandra errors for table transaction_keyspace.dda_transactions in last 24 hours"
  - Pipeline C: "Cassandra write latency for table transaction_keyspace.dda_transactions today..."

**Actual Behavior**:
- ❌ **Single query rewrite** for entire question
- Only uses **first detected intent** (schema_metadata)
- **Same rewritten query used for all three doc types**

**Actual Log Output**:
```
Query rewritten: 'What is the schema of dda_transactions, what errors occurred in the last 24 hours, and is the write latency normal today?' 
→ 'Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns' 
(doc_sub_type: schema_metadata)
```

**Impact**: 
- LOG_SUMMARY and METRIC_SUMMARY searches use the **wrong query** (schema query instead of error/latency queries)
- Results in **low similarity scores** for logs and metrics

---

### Step 5️⃣: Embedding Generation

**Status**: ✅ Executed

**Actual Behavior**:
- Single embedding generated from the schema-rewritten query
- **Same embedding used for all three doc type searches**

**Details**:
- ✅ Status: success
- ✅ Embedding dimension: 384
- ✅ Duration: ~54ms

**Expected Behavior**:
- Should generate **3 separate embeddings** (one per intent-specific rewritten query)

---

### Step 6️⃣-7️⃣: Vector Search + Candidate Selection

**Status**: ⚠️ **Partially Working** - Searches execute but with wrong queries

**Actual Results**:
```json
{
  "documents": [
    {
      "sourceType": "METADATA",
      "docSubType": "schema_metadata",
      "similarityScore": 0.944,
      "content": "Schema definition of cassandra table transaction_keyspace.dda_transactions..."
    }
  ],
  "count": 1
}
```

**Expected Results**:
```json
{
  "documents": [
    {
      "sourceType": "METADATA",
      "docSubType": "schema_metadata",
      "similarityScore": 0.944
    },
    {
      "sourceType": "LOG_SUMMARY",
      "docSubType": "logs_daily",
      "similarityScore": 0.801
    },
    {
      "sourceType": "METRIC_SUMMARY",
      "docSubType": "metrics_daily",
      "similarityScore": 0.929
    }
  ],
  "count": 3
}
```

**Actual Log Output**:
```
Searching docTypes: [METADATA, LOG_SUMMARY, METRIC_SUMMARY]

Searching: source_type=METADATA, doc_sub_type=schema_metadata
Found 1 documents for source_type=METADATA, doc_sub_type=schema_metadata (4ms)

Searching: source_type=LOG_SUMMARY, doc_sub_type=logs_daily
Found 1 documents for source_type=LOG_SUMMARY, doc_sub_type=logs_daily (7ms)

Searching: source_type=METRIC_SUMMARY, doc_sub_type=metrics_daily
Found 1 documents for source_type=METRIC_SUMMARY, doc_sub_type=metrics_daily (3ms)

Document filtered out: doc_sub_type=logs_daily, similarity 0.545 < threshold 0.63, distance=0.455
Document filtered out: doc_sub_type=metrics_daily, similarity 0.586 < threshold 0.65, distance=0.414
```

**Analysis**:
- ✅ **System searches all three doc types** (METADATA, LOG_SUMMARY, METRIC_SUMMARY)
- ✅ **Finds documents for all three types** (1 document each)
- ❌ **LOG_SUMMARY document filtered out**: similarity 0.545 < threshold 0.63
- ❌ **METRIC_SUMMARY document filtered out**: similarity 0.586 < threshold 0.65
- ✅ **METADATA document passed**: similarity 0.944 >= threshold 0.75

**Root Cause**:
- LOG_SUMMARY and METRIC_SUMMARY documents were searched using the **schema query embedding** instead of error/latency-specific queries
- This resulted in **low similarity scores** (0.545 and 0.586) that failed threshold filtering
- When tested individually with correct queries:
  - LOG_SUMMARY (errors query): similarity 0.801 ✅
  - METRIC_SUMMARY (latency query): similarity 0.929 ✅

---

### Step 8️⃣: Prompt Construction

**Status**: ✅ **Working** - Prompt builder groups by doc type

**Actual Behavior**:
- Prompt builder already supports grouping documents by `source_type`
- Creates sections: `=== METADATA ===`, `=== LOG_SUMMARY ===`, `=== METRIC_SUMMARY ===`
- **But only receives 1 document** (METADATA) due to filtering issues above

**Expected Behavior**:
- Should receive 3 documents (one per intent)
- Should create 3 sections in prompt:
  ```
  === METADATA ===
  [Schema document]
  
  === LOG_SUMMARY ===
  [Error logs document]
  
  === METRIC_SUMMARY ===
  [Latency metrics document]
  ```

**Actual Prompt Structure** (if all documents passed):
```
You are an enterprise data platform assistant.
...

User Question: What is the schema of dda_transactions, what errors occurred in the last 24 hours, and is the write latency normal today?

Context:
================================================================================

=== METADATA ===
[1] Cassandra - transaction_keyspace.dda_transactions (Date: 2025-12-05) [Relevance: 94.4%]
Schema definition of cassandra table transaction_keyspace.dda_transactions...
```

---

## 📊 Summary: Actual vs Expected

| Step | Component | Expected | Actual | Status |
|------|-----------|----------|--------|--------|
| 3️⃣ | Multi-Intent Detection | 3 intents detected | 2 intents detected (missing LOG_SUMMARY) | ⚠️ Partial |
| 4️⃣ | Query Rewriting | 3 separate queries | 1 query (schema only) | ❌ Not Working |
| 5️⃣ | Embedding Generation | 3 embeddings | 1 embedding | ❌ Not Working |
| 6️⃣-7️⃣ | Vector Search | 3 documents retrieved | 1 document retrieved | ⚠️ Partial |
| 8️⃣ | Prompt Construction | 3 sections | 1 section (only METADATA) | ⚠️ Partial |

---

## 🔍 Root Cause Analysis

### ❌ **Primary Issue: Single Query Rewrite for Multi-Intent**

**Current Implementation**:
1. Detects primary `doc_sub_type` (schema_metadata) from entire question
2. Uses single query rewrite template for that `doc_sub_type`
3. Generates single embedding from rewritten query
4. Uses same embedding to search all doc types

**Expected Implementation** (P0 Item #1):
1. **Decompose** multi-intent question into separate intent tasks
2. **Parallel query rewriting**: One rewrite per `doc_sub_type`
3. **Parallel embedding generation**: One embedding per rewritten query
4. **Parallel vector searches**: One search per doc type with correct embedding
5. **Merge results** into unified prompt

---

## ✅ What's Working

1. ✅ **Multi-doc-type search execution**: System searches all requested doc types
2. ✅ **Document retrieval**: Finds documents for all three types
3. ✅ **Prompt grouping**: Prompt builder groups by `source_type`
4. ✅ **Individual intent searches work**: When tested separately with correct queries, all three return documents

---

## ❌ What's Not Working

1. ❌ **Query decomposition**: Single query rewrite instead of per-intent rewrites
2. ❌ **Embedding generation**: Single embedding instead of per-intent embeddings
3. ❌ **Intent detection**: Missing LOG_SUMMARY in automatic detection
4. ❌ **Threshold filtering**: LOG_SUMMARY and METRIC_SUMMARY filtered out due to wrong query

---

## 🎯 Validation Against Expected Behavior

### Expected Multi-Intent Flow:

```
🔹 PIPELINE A — SCHEMA (METADATA)
  5A ✅ Query Rewriting (Schema) → "Schema definition of cassandra table..."
  6A ✅ Embedding Generation → 384-dim vector
  7A ✅ Filtered Vector Search → doc_type='METADATA', doc_sub_type='schema_metadata'
  8A ✅ Schema Document Selected → similarity 0.944 ✅

🔹 PIPELINE B — ERRORS (LOGS, LAST 24 HOURS)
  5B ❌ Query Rewriting (Errors) → NOT IMPLEMENTED (uses schema query)
  6B ❌ Embedding Generation → NOT IMPLEMENTED (uses schema embedding)
  7B ⚠️ Filtered Vector Search → Finds document but wrong query
  8B ❌ Error Documents Selected → Filtered out (similarity 0.545 < 0.63)

🔹 PIPELINE C — WRITE LATENCY (METRICS, TODAY)
  5C ❌ Query Rewriting (Latency) → NOT IMPLEMENTED (uses schema query)
  6C ❌ Embedding Generation → NOT IMPLEMENTED (uses schema embedding)
  7C ⚠️ Filtered Vector Search → Finds document but wrong query
  8C ❌ Latency Metrics Selected → Filtered out (similarity 0.586 < 0.65)
```

### Actual Results:

- **Pipeline A (Schema)**: ✅ **Working** - Document retrieved with similarity 0.944
- **Pipeline B (Errors)**: ❌ **Not Working** - Document found but filtered out (wrong query)
- **Pipeline C (Latency)**: ❌ **Not Working** - Document found but filtered out (wrong query)

---

## 📝 Recommendations

### 🔥 P0 Priority Fixes Needed:

1. **Implement Multi-Intent Query Decomposition**
   - Parse question into separate intent sub-questions
   - Create separate query rewrites per intent
   - Generate separate embeddings per intent

2. **Parallel Pipeline Execution**
   - Execute three pipelines in parallel (or sequentially with correct queries)
   - Merge results before threshold filtering

3. **Improve Intent Detection**
   - Enhance detection to catch "errors" → LOG_SUMMARY
   - Support time-based intent detection ("last 24 hours" → logs_daily)

4. **Multi-Section Prompt Construction**
   - Already implemented ✅
   - Just needs all three documents to pass filtering

---

## 🎯 Conclusion

**Current State**: 
- ⚠️ **Partially Working** - System architecture supports multi-intent, but implementation uses single query for all intents
- ✅ **Individual searches work** when tested separately with correct queries
- ❌ **Multi-intent question fails** because it uses wrong query for LOG_SUMMARY and METRIC_SUMMARY

**Gap**: 
- Missing **P0 Item #1: Multi-Intent Query Execution & Prompt Fusion**
- Need to implement per-intent query rewriting and embedding generation

**Next Steps**:
1. Implement query decomposition for multi-intent questions
2. Generate separate embeddings per intent
3. Execute parallel searches with correct embeddings
4. Merge results into unified multi-section prompt

---

**Test Date**: 2025-12-05  
**Version**: V1  
**Status**: ⚠️ **Partially Working** - Needs P0 Implementation

