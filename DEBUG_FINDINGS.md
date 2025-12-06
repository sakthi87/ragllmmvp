# Vector Search Debugging - Findings & Analysis

## 🔍 Investigation Summary

### Issue
Vector search returns **0 documents** despite:
- ✅ 12 documents exist in database
- ✅ All have correct `table_name`, `keyspace`, `cluster_name`
- ✅ Embeddings are present (384-dim vectors)
- ✅ Direct PostgreSQL query shows similarity = 1.0

---

## 📋 Code Analysis

### 1. Embedding Format ✅ CORRECT
**Location**: `VectorSearchService.formatEmbedding()`
```java
private String formatEmbedding(List<Double> embedding) {
    return "[" + embedding.stream()
        .map(d -> String.format("%.6f", d))
        .collect(Collectors.joining(",")) + "]";
}
```
**Output**: `[0.123456,0.234567,...]` ✅ Correct PostgreSQL vector format

### 2. SQL Query Structure ✅ CORRECT
**Location**: `RagDocumentRepository.findSimilarDocumentsBySourceType()`
```sql
SELECT id, cluster_name, source_type, doc_sub_type, ..., embedding, created_at,
       1 - (embedding <=> CAST(:embedding AS vector)) as similarity
FROM rag_documents
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
  AND source_type = :sourceType
  AND (:docSubType IS NULL OR doc_sub_type = :docSubType)
  AND table_name = COALESCE(:tableName, table_name)
  AND keyspace = COALESCE(:keyspace, keyspace)
  AND (:startDate IS NULL OR event_date >= :startDate::DATE)
  AND (:endDate IS NULL OR event_date <= :endDate::DATE)
ORDER BY embedding <=> CAST(:embedding AS vector)
LIMIT :topK
```

### 3. Parameter Binding Analysis

#### Issue 1: Date Range Filtering ⚠️ POTENTIAL PROBLEM
**Location**: `VectorSearchService.searchByDocType()` line 245
```java
LocalDate[] dateRange = parseTimeRange("7d"); // Default to last 7 days
```

**Problem**: 
- All documents have `event_date` set (verified: `has_date = true`)
- If `parseTimeRange("7d")` returns dates that don't match document dates → **0 results**
- Documents have `event_date = '2025-11-28'` (future date)
- If `dateRange[0]` is `2025-12-05 - 7 days = 2025-11-28` and `dateRange[1]` is `2025-12-05`, then:
  - `event_date >= 2025-11-28` ✅ Should pass
  - `event_date <= 2025-12-05` ✅ Should pass
- **BUT**: If `parseTimeRange` has a bug or timezone issue → dates might not match

#### Issue 2: COALESCE with NULL ⚠️ POTENTIAL PROBLEM
**Location**: SQL WHERE clause
```sql
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
```

**Problem**:
- When `clusterName = null`, `COALESCE(null, cluster_name)` should return `cluster_name`
- **BUT**: In YugabyteDB/PostgreSQL, if parameter is explicitly `NULL`, it might not work as expected
- Documents have `cluster_name = 'cass-prod-1'`
- If `searchCluster = null` is passed, COALESCE should work, but might not in all cases

#### Issue 3: docSubType Filter ⚠️ POTENTIAL PROBLEM
**Location**: SQL WHERE clause
```sql
AND (:docSubType IS NULL OR doc_sub_type = :docSubType)
```

**Problem**:
- If `docSubType = 'schema_metadata'` is passed, it should match
- **BUT**: If `docSubType` is `null`, the condition `(:docSubType IS NULL OR ...)` should pass all rows
- Need to verify what value is actually passed

---

## 🧪 Test Cases to Run

### Test 1: Direct SQL Query (No Date Filter)
```sql
SELECT COUNT(*) 
FROM rag_documents
WHERE table_name = 'dda_transactions' 
  AND keyspace = 'transaction_keyspace'
  AND source_type = 'METADATA'
  AND doc_sub_type = 'schema_metadata';
```
**Expected**: Should return 1 row

### Test 2: Direct SQL Query (With COALESCE)
```sql
SELECT COUNT(*) 
FROM rag_documents
WHERE cluster_name = COALESCE(NULL, cluster_name)
  AND table_name = COALESCE('dda_transactions', table_name)
  AND keyspace = COALESCE('transaction_keyspace', keyspace)
  AND source_type = 'METADATA';
```
**Expected**: Should return 5 rows (all METADATA docs)

### Test 3: Direct SQL Query (With Date Filter)
```sql
SELECT COUNT(*) 
FROM rag_documents
WHERE table_name = 'dda_transactions'
  AND (NULL IS NULL OR event_date >= NULL::DATE)
  AND (NULL IS NULL OR event_date <= NULL::DATE);
```
**Expected**: Should return all 12 rows (NULL dates should pass)

### Test 4: Direct SQL Query (With Actual Dates)
```sql
SELECT COUNT(*) 
FROM rag_documents
WHERE table_name = 'dda_transactions'
  AND event_date >= '2025-11-28'::DATE
  AND event_date <= '2025-12-05'::DATE;
```
**Expected**: Should return all 12 rows (all have event_date = '2025-11-28')

### Test 5: Vector Similarity Query (Manual)
```sql
-- Get an embedding from a document
SELECT embedding FROM rag_documents WHERE doc_sub_type = 'schema_metadata' LIMIT 1;

-- Use that embedding to test similarity
SELECT 1 - (embedding <=> '[paste embedding here]') AS similarity
FROM rag_documents
WHERE table_name = 'dda_transactions'
  AND source_type = 'METADATA';
```
**Expected**: Should return similarity scores > 0.75

---

## 🔧 Recommended Fixes

### Fix 1: Simplify Date Filtering
**Current**: Always passes date range (even if NULL)
**Fix**: Only add date filters if time range is specified

```java
// In searchByDocType()
LocalDate startDate = null;
LocalDate endDate = null;
// Only set dates if time range is explicitly requested
// For now, pass null to allow all documents
```

### Fix 2: Fix COALESCE Usage
**Current**: `COALESCE(:clusterName, cluster_name)`
**Fix**: Use explicit NULL check

```sql
WHERE (:clusterName IS NULL OR cluster_name = :clusterName)
  AND (:tableName IS NULL OR table_name = :tableName)
  AND (:keyspace IS NULL OR keyspace = :keyspace)
```

### Fix 3: Add Debug Logging
**Add**: Log actual SQL parameters being passed

```java
log.debug("SQL Parameters: clusterName={}, tableName={}, keyspace={}, startDate={}, endDate={}, docSubType={}", 
    clusterName, tableName, keyspace, dateRange[0], dateRange[1], docSubType);
```

### Fix 4: Test with Minimal Filters
**Create**: A test query with only required filters

```java
// Test query with minimal filters
List<Object[]> results = repository.findSimilarDocumentsBySourceType(
    embeddingStr, 
    null,        // clusterName - no filter
    docType,     // source_type - required
    docSubType,  // doc_sub_type - specific
    null,        // tableName - no filter
    null,        // keyspace - no filter
    null,        // startDate - no filter
    null,        // endDate - no filter
    topK
);
```

---

## 📊 Expected vs Actual

### Expected Behavior
1. Query with `source_type='METADATA'`, `doc_sub_type='schema_metadata'` should return 1 document
2. Similarity score should be > 0.75 (high match)
3. Document should pass threshold filter

### Actual Behavior
1. Query returns 0 documents (before threshold filtering)
2. No SQL errors in logs
3. Query executes successfully but finds no rows

---

## 🎯 Root Cause Hypothesis

**Most Likely**: **Date Range Filtering Issue**

**Reasoning**:
1. All documents have `event_date = '2025-11-28'`
2. `parseTimeRange("7d")` might return dates that don't match
3. If `startDate` or `endDate` is incorrectly calculated → all documents filtered out
4. Date comparison `event_date >= :startDate::DATE` might fail if date format is wrong

**Second Most Likely**: **COALESCE with NULL Parameter**

**Reasoning**:
1. When `clusterName = null`, `COALESCE(null, cluster_name)` should work
2. But in some PostgreSQL/YugabyteDB versions, NULL parameters might not work as expected
3. If COALESCE doesn't handle NULL correctly → all rows filtered out

---

## ✅ Next Steps

1. **Run Test Cases 1-5** to identify exact issue
2. **Add debug logging** to see actual SQL parameters
3. **Simplify filters** one by one to isolate problem
4. **Fix identified issue** and verify
5. **Test threshold filtering** once documents are retrieved

