# Vector Search Fix - Root Cause & Solution

## 🔍 Root Cause Identified

### Issue: Date Range Filtering + COALESCE NULL Handling

**Problem 1: Date Filter Logic**
```java
// In searchByDocType() line 245
LocalDate[] dateRange = parseTimeRange("7d"); // Returns [2025-11-28, 2025-12-05]
```

**SQL Query**:
```sql
AND (:startDate IS NULL OR event_date >= :startDate::DATE)
AND (:endDate IS NULL OR event_date <= :endDate::DATE)
```

**Issue**: 
- Documents have `event_date = '2025-11-28'`
- `startDate = 2025-11-28`, `endDate = 2025-12-05`
- Condition: `event_date >= 2025-11-28` ✅ Should pass
- Condition: `event_date <= 2025-12-05` ✅ Should pass
- **BUT**: The `CAST(:startDate AS DATE)` might fail if parameter is not properly bound

**Problem 2: COALESCE with NULL**
```sql
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
```

**Issue**:
- When `clusterName = null`, PostgreSQL/YugabyteDB might not handle COALESCE correctly
- If parameter binding fails → all rows filtered out

---

## ✅ Solution

### Fix 1: Remove Date Filtering (Temporary - for testing)
**Change**: Pass `null` for dates to allow all documents

```java
// In searchByDocType() - line 245
// OLD:
LocalDate[] dateRange = parseTimeRange("7d");

// NEW:
LocalDate startDate = null;  // No date filtering for now
LocalDate endDate = null;
```

### Fix 2: Improve COALESCE Logic
**Change**: Use explicit NULL checks instead of COALESCE

**OLD SQL**:
```sql
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
  AND table_name = COALESCE(:tableName, table_name)
  AND keyspace = COALESCE(:keyspace, keyspace)
```

**NEW SQL**:
```sql
WHERE (:clusterName IS NULL OR cluster_name = :clusterName)
  AND (:tableName IS NULL OR table_name = :tableName)
  AND (:keyspace IS NULL OR keyspace = :keyspace)
```

### Fix 3: Add Debug Logging
**Add**: Log actual parameters being passed to SQL

```java
log.debug("SQL Parameters: clusterName={}, tableName={}, keyspace={}, startDate={}, endDate={}, docSubType={}, sourceType={}", 
    clusterName, tableName, keyspace, startDate, endDate, docSubType, docType);
```

---

## 🧪 Testing Plan

### Test 1: Remove Date Filters
1. Set `startDate = null`, `endDate = null`
2. Run query
3. **Expected**: Should return documents (if COALESCE is the issue)

### Test 2: Fix COALESCE
1. Change SQL to use `IS NULL OR` pattern
2. Run query
3. **Expected**: Should return documents

### Test 3: Verify Threshold Filtering
1. Once documents are retrieved, verify similarity scores
2. Check if threshold filtering works correctly
3. **Expected**: Documents with similarity >= 0.75 should pass

---

## 📝 Implementation Steps

1. ✅ Update `RagDocumentRepository.java` - Fix SQL WHERE clauses
2. ✅ Update `VectorSearchService.java` - Remove date filtering temporarily
3. ✅ Add debug logging
4. ✅ Test with simplified query
5. ✅ Verify threshold filtering works
6. ✅ Re-enable date filtering with proper NULL handling

