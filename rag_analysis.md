# RAG Intent Detection Analysis

## 📋 Production-Ready Implementation: 12 Canonical Document Types

### ✅ Updated Architecture (Hybrid 2-Level Typing)

**Current Implementation:**
- `source_type`: High-level categories (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
- `doc_sub_type`: **12 fine-grained canonical types** (NOT NULL, required for all documents)
- Uses `source_type` + `doc_sub_type` combination for precise filtering

**12 Canonical Document Types:**
1. `business_metadata` - Domain, owner, business context
2. `schema_metadata` - Primary key, columns, TTL, DDL
3. `storage_configuration` - Compaction, caching, bloom filters
4. `table_statistics` - Size, row count, partition count
5. `data_lifecycle` - TTL, retention, archival, purge policies
6. `lineage_kafka` - Upstream Kafka topic information
7. `lineage_spark` - Spark processing job details
8. `lineage_dataapi` - Downstream API usage
9. `logs_daily` - Last 24h log summaries
10. `logs_weekly` - 7-day log trends
11. `metrics_daily` - Last 24h performance metrics
12. `metrics_weekly` - 7-day performance trends

---

## 🎯 Core Principles: How Data Must Exist in Vector DB

### ✅ What NOT to Store
- ❌ One giant document per table
- ❌ Raw logs line-by-line
- ❌ Full metrics time series

### ✅ What TO Store
**Atomic, intention-aware documents** - Each row in `rag_documents` = 1 meaningful cognitive unit.

Documents are grouped by purpose:
- **Metadata Docs** → Answer WHAT, WHO, WHY
- **Schema Docs** → Answer HOW data is structured
- **Storage Docs** → Answer WHY performance behaves
- **Lifecycle Docs** → Answer HOW LONG data lives
- **Lineage Docs** → Answer HOW data flows
- **Logs Summary Docs** → Answer WHAT broke
- **Metrics Summary Docs** → Answer WHEN & HOW badly

---

## 📊 Canonical Document Groups (7 Groups)

### Group A — Metadata (Static)
| source_type | component | Purpose |
|-------------|-----------|---------|
| `business_metadata` | Cassandra | Ownership, domain |
| `domain_metadata` | Platform | Business classification |

### Group B — Schema (Static)
| source_type | Purpose |
|-------------|---------|
| `schema_metadata` | Columns, PK, CK, TTL |

### Group C — Storage & Performance (Semi-static)
| source_type | Purpose |
|-------------|---------|
| `storage_configuration` | Compaction, bloom, GC |
| `table_statistics` | Row count, size |

### Group D — Data Lifecycle (Static)
| source_type | Purpose |
|-------------|---------|
| `data_lifecycle` | Retention, archival, purge |

### Group E — Lineage (Static)
| source_type | component |
|-------------|-----------|
| `lineage_kafka` | Kafka |
| `lineage_spark` | Spark |
| `lineage_cassandra` | Cassandra |
| `lineage_dataapi` | DataAPI |

### Group F — Logs (Rolling, Summary Only)
| source_type | Time Scope |
|-------------|------------|
| `logs_daily` | Last 24h |
| `logs_weekly` | Last 7 days |

### Group G — Metrics (Rolling, Summary Only)
| source_type | Time Scope |
|-------------|------------|
| `metrics_daily` | Last 24h |
| `metrics_weekly` | Last 7 days |

---

## 📈 How One Cassandra Table Becomes ~15–20 Vector Documents

For one table: `dda_transactions`, you will load:

| Doc # | source_type | Purpose |
|-------|-------------|---------|
| 1 | `business_metadata` | Domain, owner |
| 2 | `schema_metadata` | PK, columns |
| 3 | `storage_configuration` | Compaction |
| 4 | `table_statistics` | Size, rows |
| 5 | `data_lifecycle` | TTL, retention |
| 6 | `lineage_kafka` | Kafka topic |
| 7 | `lineage_spark` | Spark job |
| 8 | `lineage_dataapi` | API usage |
| 9 | `logs_daily` | Last 24h failures |
| 10 | `logs_weekly` | 7-day trend |
| 11 | `metrics_daily` | Read/write latency |
| 12 | `metrics_weekly` | Throughput trends |

**This is how you scale to 500 tables safely.**

---

## 🔄 Data Loading Pipeline

```
Source System
   ↓
Raw Extract (JSON)
   ↓
Semantic Document Builder
   ↓
Embedding Generator (MiniLM)
   ↓
Vector Loader (Yugabyte)
```

### Example: Metadata Builder (Cassandra)

**Raw Metadata Input:**
```json
{
  "keyspace": "transaction_keyspace",
  "table": "dda_transactions",
  "domain": "Retail Banking",
  "sub_domain": "Digital Deposit Accounts",
  "owner": "Payments Team",
  "environment": "PROD"
}
```

**Generated Vector Document:**
```json
{
  "source_type": "business_metadata",
  "component": "Cassandra",
  "source_name": "dda_transactions",
  "keyspace": "transaction_keyspace",
  "table_name": "dda_transactions",
  "domain": "Retail Banking",
  "sub_domain": "Digital Deposit Accounts",
  "content": "The dda_transactions table belongs to the Retail Banking domain under Digital Deposit Accounts. It is owned by the Payments Team and runs in the Production environment.",
  "metadata": {
    "owner": "Payments Team",
    "environment": "PROD"
  }
}
```

**This becomes 1 embedding row.**

### Example: Logs Summary Builder (Kafka)

**Raw Logs (Millions):**
```
2025-09-14 ERROR Kafka timeout partition 3
2025-09-14 ERROR Kafka rebalance failure
2025-09-15 WARN Kafka lag increased
```

**Aggregated Weekly Vector Log Doc:**
```json
{
  "source_type": "logs_weekly",
  "component": "Kafka",
  "table_name": "dda_transactions",
  "time_window": "last_7_days",
  "content": "Kafka ingestion experienced 14 timeouts and 3 rebalance failures in the last 7 days. Peak consumer lag reached 2.3M messages on Sept 14.",
  "metadata": {
    "timeout_count": 14,
    "rebalance_failures": 3,
    "max_lag": "2.3M"
  }
}
```

**This prevents vector DB from exploding in size.**

### Example: Metrics Summary Builder (Cassandra)

**Raw Metrics:**
- p99 latency every second
- Throughput per node
- Pending compactions

**Weekly Summary Vector Doc:**
```json
{
  "source_type": "metrics_weekly",
  "component": "Cassandra",
  "table_name": "dda_transactions",
  "content": "For dda_transactions, the 7-day average read latency was 38ms, write latency was 62ms, with 3 major compaction spikes and tombstone ratio reaching 22%.",
  "metadata": {
    "avg_read_ms": 38,
    "avg_write_ms": 62,
    "tombstone_ratio": 22
  }
}
```

---

## 🎯 How Grouping Affects Retrieval & Accuracy

**Example Query:** "Why is dda_transactions slow today?"

**Auto-selected source_type values:**
- `metrics_daily`
- `logs_daily`
- `storage_configuration`

**NOT selected:**
- `business_metadata`
- `domain_metadata`
- `data_lifecycle`

**This is how you:**
- ✅ Avoid hallucinations
- ✅ Constrain Phi-4 context
- ✅ Ensure RCA correctness

---

## Reference Intent-to-SQL Mapping (Updated - Production Ready)

Based on the production-ready specification, intents map to SQL WHERE clauses using **2-level typing** (source_type + doc_sub_type):

| Intent     | SQL WHERE                                                     |
| ---------- | ------------------------------------------------------------- |
| Schema     | `source_type='METADATA' AND doc_sub_type='schema_metadata'`   |
| Domain     | `source_type='METADATA' AND doc_sub_type='business_metadata'` |
| Storage    | `source_type='METADATA' AND doc_sub_type='storage_configuration'` |
| Statistics | `source_type='METADATA' AND doc_sub_type='table_statistics'` |
| Lifecycle  | `source_type='METADATA' AND doc_sub_type='data_lifecycle'`   |
| Kafka Lineage | `source_type='LINEAGE' AND doc_sub_type='lineage_kafka'` |
| Spark Lineage | `source_type='LINEAGE' AND doc_sub_type='lineage_spark'` |
| API Lineage | `source_type='LINEAGE' AND doc_sub_type='lineage_dataapi'` |
| Slowness (Today) | `source_type IN ('LOG_SUMMARY','METRIC_SUMMARY') AND doc_sub_type IN ('logs_daily','metrics_daily')` |
| Slowness (Week) | `source_type IN ('LOG_SUMMARY','METRIC_SUMMARY') AND doc_sub_type IN ('logs_weekly','metrics_weekly')` |
| RCA        | `source_type IN ('LOG_SUMMARY','METRIC_SUMMARY','LINEAGE')` (all doc_sub_types) |

**Note:** This hybrid approach uses high-level `source_type` for broad categorization and granular `doc_sub_type` for precise filtering. Both fields are required (NOT NULL) in the production schema.

---

## 🚀 Production-Grade Improvements: Query Rewriting & Per-Doc-Type Thresholds

### ✅ Query Rewriting Implementation

**Problem Solved:**
- User questions like "What is the schema?" are too short and ambiguous
- Embeddings become weak vectors → similarity stuck at 0.60-0.72
- Need canonical, domain-specific queries for better semantic matching

**Solution Implemented:**
- ✅ `QueryRewriteService` - Rewrites user questions using canonical templates
- ✅ `query-rewrite-templates.json` - JSON configuration with 12 rewrite templates
- ✅ Template-based rewriting: `{keyspace}` and `{table}` placeholders replaced dynamically
- ✅ Rewriting happens **after intent detection, before embedding generation**

**Example:**
```
User Question: "What is the schema?"
↓
Intent Detection: doc_sub_type = "schema_metadata"
↓
Query Rewriting: "Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns"
↓
Embedding Generation: Uses rewritten query
↓
Result: Similarity jumps from 0.65 → 0.82-0.90
```

**Files:**
- `mvp/backend/src/main/resources/query-rewrite-templates.json` - 12 rewrite templates
- `mvp/backend/src/main/java/com/yugabyte/rag/service/QueryRewriteService.java` - Rewriting logic
- `mvp/backend/src/main/java/com/yugabyte/rag/config/QueryRewriteConfigLoader.java` - JSON loader

### ✅ Per-Doc-Type Similarity Thresholds

**Problem Solved:**
- Single global threshold (0.75) too high for logs/metrics
- Single global threshold (0.65) too low for schema/business metadata
- Need different thresholds per document type for optimal filtering

**Solution Implemented:**
- ✅ Per-doc-type thresholds configured in `query-rewrite-templates.json`
- ✅ Each document uses its own `doc_sub_type` threshold for filtering
- ✅ Default threshold (0.65) used if doc_sub_type not configured

**Threshold Configuration:**
| doc_sub_type | Threshold | Rationale |
|--------------|-----------|-----------|
| `schema_metadata` | 0.75 | High precision needed, good semantic matching |
| `business_metadata` | 0.75 | High precision needed, good semantic matching |
| `storage_configuration` | 0.72 | Medium precision, technical terms |
| `table_statistics` | 0.70 | Medium precision, numeric data |
| `data_lifecycle` | 0.72 | Medium precision, policy-focused |
| `lineage_kafka` | 0.75 | High precision, clear relationships |
| `lineage_spark` | 0.75 | High precision, clear relationships |
| `lineage_dataapi` | 0.75 | High precision, clear relationships |
| `logs_daily` | 0.63 | Lower threshold, error text varies |
| `logs_weekly` | 0.65 | Lower threshold, trend analysis |
| `metrics_daily` | 0.65 | Lower threshold, numeric variations |
| `metrics_weekly` | 0.67 | Lower threshold, trend analysis |

**Implementation:**
- `VectorSearchService` applies per-doc-type thresholds during filtering
- Each document's `doc_sub_type` determines its threshold
- Logs threshold statistics for monitoring

### ✅ Complete RAG Flow with Improvements

**Updated Flow:**
```
1. User Question → React UI
2. POST /api/rag/ask → Spring Boot
3. Intent Detection (JSON-based) → doc_sub_type detected
4. ✅ Query Rewriting → Canonical query generated
5. Embedding Generation → Uses rewritten query (higher similarity)
6. Vector Search → Yugabyte PGVector with doc_sub_type filtering
7. ✅ Per-Doc-Type Threshold Filtering → Each doc uses its threshold
8. Prompt Building → Structured prompt with retrieved docs
9. Phi-4 Q3 Generation → Grounded answer
10. Response → React UI
```

**Key Improvements:**
- ✅ Query rewriting improves embedding similarity by +0.10-0.20
- ✅ Per-doc-type thresholds optimize filtering per document type
- ✅ JSON-based configuration for easy maintenance
- ✅ Atomic document design ensures high-quality embeddings

---

## 🚨 CRITICAL ARCHITECTURAL MISMATCH

### Current Implementation vs. New Specification

**Current Architecture (2-Level Typing):**
- `source_type`: High-level categories (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
- `doc_sub_type`: Fine-grained sub-types (schema_metadata, business_metadata, storage_configuration, data_lifecycle)
- Uses `source_type` + `doc_sub_type` combination for filtering

**New Specification (1-Level Granular Typing):**
- `source_type`: **Direct document purpose** (business_metadata, schema_metadata, storage_configuration, table_statistics, data_lifecycle, lineage_kafka, lineage_spark, lineage_cassandra, lineage_dataapi, logs_daily, logs_weekly, metrics_daily, metrics_weekly)
- `doc_sub_type`: **Not used** (or repurposed for different use case)
- Uses `source_type` alone for filtering

**This is a FUNDAMENTAL architectural difference that requires a complete refactoring.**

---

## Current Implementation Analysis

### ✅ What's Currently Implemented

#### 1. Intent Detection Service (`IntentDetectionService.java`)

**Keyword-to-SourceType Mapping:**
- Maps keywords to `source_type` values: **METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY** (high-level)
- Supports multiple `source_type` values per keyword (e.g., RCA returns LOG_SUMMARY, METRIC_SUMMARY, LINEAGE)
- Keyword examples:
  - Schema: "schema", "primary key", "columns", "ddl", "table structure"
  - Domain: "domain", "owner", "pii", "data owner", "business"
  - Tombstones: "tombstone", "compaction", "storage"
  - Slowness: "latency", "lag", "slow", "bottleneck"
  - RCA: "why", "root cause", "what caused", "rca"

**Keyword-to-DocSubType Mapping:**
- Maps keywords to `doc_sub_type` values for fine-grained filtering
- Used only when `source_type = METADATA`
- Sub-types: `schema_metadata`, `business_metadata`, `storage_configuration`, `data_lifecycle`

**❌ MISMATCH:** Current code expects high-level `source_type` values, but new spec uses granular `source_type` values directly.

#### 2. SQL Query Structure (`RagDocumentRepository.findSimilarDocumentsBySourceType`)

```sql
SELECT id, cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
       keyspace, table_name, domain, sub_domain, event_date, time_window, content, metadata,
       embedding, created_at,
       1 - (embedding <=> CAST(:embedding AS vector)) as similarity
FROM rag_documents
WHERE cluster_name = COALESCE(:clusterName, cluster_name)
  AND source_type = :sourceType
  AND (:docSubType IS NULL OR doc_sub_type = :docSubType)
  AND table_name = COALESCE(:tableName, table_name)
  AND keyspace = COALESCE(:keyspace, keyspace)
  AND (:startDate IS NULL OR event_date >= :startDate)
  AND (:endDate IS NULL OR event_date <= :endDate)
ORDER BY embedding <=> CAST(:embedding AS vector)
LIMIT :topK
```

#### 3. Vector Search Logic (`VectorSearchService.searchVectors`)

- For each detected `source_type`, calls `searchByDocType()`
- For METADATA queries: Uses `doc_sub_type` when detected
- For other types (LINEAGE, LOG_SUMMARY, METRIC_SUMMARY): Ignores `doc_sub_type`
- Applies similarity threshold filtering (default: 0.75)
- Sorts by similarity and limits results

---

## 🔍 Gap Analysis

### Comparison Table: Current vs. New Specification

| Intent | New Spec SQL | Current Behavior | Status | Gap Description |
|--------|--------------|------------------|--------|-----------------|
| **Schema** | `source_type='schema_metadata'` | ❌ Searches `source_type='METADATA' AND doc_sub_type='schema_metadata'` | ❌ **MAJOR GAP** | **Architectural mismatch**: New spec uses `source_type='schema_metadata'` directly, current uses 2-level typing |
| **Domain** | `source_type='business_metadata'` | ❌ Searches `source_type='METADATA' AND doc_sub_type='business_metadata'` | ❌ **MAJOR GAP** | **Architectural mismatch**: New spec uses `source_type='business_metadata'` directly |
| **Tombstones** | `source_type='storage_configuration'` | ❌ Searches `source_type='METADATA' AND doc_sub_type='storage_configuration'` | ❌ **MAJOR GAP** | **Architectural mismatch**: New spec uses `source_type='storage_configuration'` directly |
| **Slowness** | `source_type IN ('logs_daily','logs_weekly','metrics_daily','metrics_weekly')` | ❌ Searches `source_type IN ('LOG_SUMMARY','METRIC_SUMMARY')` | ❌ **MAJOR GAP** | **Value mismatch**: New spec uses granular types (logs_daily, metrics_daily), current uses high-level (LOG_SUMMARY, METRIC_SUMMARY) |
| **RCA** | `source_type IN ('logs_daily','logs_weekly','metrics_daily','metrics_weekly','lineage_kafka','lineage_spark','lineage_cassandra','lineage_dataapi')` | ❌ Searches `source_type IN ('LOG_SUMMARY','METRIC_SUMMARY','LINEAGE')` | ❌ **MAJOR GAP** | **Value mismatch**: New spec uses granular lineage types (lineage_kafka, lineage_spark, etc.), current uses high-level LINEAGE |

---

## ❌ Critical Gaps Identified

### 1. **🚨 FUNDAMENTAL ARCHITECTURAL MISMATCH - source_type Values**

**Issue:**
- **New Specification:** Uses granular `source_type` values that directly represent document purposes:
  - `business_metadata`, `schema_metadata`, `storage_configuration`, `table_statistics`, `data_lifecycle`
  - `lineage_kafka`, `lineage_spark`, `lineage_cassandra`, `lineage_dataapi`
  - `logs_daily`, `logs_weekly`, `metrics_daily`, `metrics_weekly`
- **Current Implementation:** Uses high-level `source_type` values:
  - `METADATA`, `LINEAGE`, `LOG_SUMMARY`, `METRIC_SUMMARY`
  - Relies on `doc_sub_type` for fine-grained filtering

**Impact:**
- **Complete incompatibility** - Current code cannot query documents stored with new specification
- All intent detection logic needs to be rewritten
- All SQL queries need to be updated
- Data migration required if existing data uses old structure

**Required Fix:**
- **Option A (Recommended):** Migrate to new architecture
  - Update `source_type` values to granular types
  - Remove dependency on `doc_sub_type` for primary filtering
  - Update all intent detection to map to new `source_type` values
  - Update SQL queries to use new `source_type` values directly
- **Option B:** Maintain backward compatibility
  - Support both old and new `source_type` values
  - Add mapping layer to convert between old and new formats
  - More complex but allows gradual migration

### 2. **Missing source_type Values**

**Issue:**
- New specification includes `source_type` values not present in current implementation:
  - `domain_metadata` (Platform-level business classification)
  - `table_statistics` (Row count, size)
  - `lineage_kafka`, `lineage_spark`, `lineage_cassandra`, `lineage_dataapi` (Component-specific lineage)
  - `logs_daily`, `logs_weekly` (Time-scoped logs)
  - `metrics_daily`, `metrics_weekly` (Time-scoped metrics)

**Impact:**
- Cannot store or query documents with these `source_type` values
- Intent detection cannot route to component-specific lineage types
- Cannot distinguish between daily vs. weekly logs/metrics

**Required Fix:**
- Add all missing `source_type` values to intent detection service
- Update keyword mappings to route to appropriate granular types
- Add support for time-scoped queries (daily vs. weekly)

### 3. **Time-Scoped Document Support**

**Issue:**
- New specification requires time-scoped documents:
  - `logs_daily` (Last 24h)
  - `logs_weekly` (Last 7 days)
  - `metrics_daily` (Last 24h)
  - `metrics_weekly` (Last 7 days)
- Current implementation uses `event_date` filtering but doesn't distinguish daily vs. weekly summaries

**Impact:**
- Cannot properly route queries to time-appropriate documents
- "Why is dda_transactions slow today?" should query `metrics_daily` and `logs_daily`, not weekly summaries

**Required Fix:**
- Add time-scope detection to intent service
- Route "today" queries to `*_daily` types
- Route "this week" queries to `*_weekly` types
- Update SQL queries to filter by `source_type` based on time scope

---

## 🤔 Missing Architectural Pieces

### 1. **Semantic Document Builder**

**Current State:**
- Documents are loaded directly from raw JSON
- No semantic transformation layer
- No aggregation/summarization of logs/metrics

**New Specification Requires:**
- **Semantic Document Builder** that transforms raw data into intention-aware documents
- Aggregates millions of log lines into summary documents
- Converts time-series metrics into daily/weekly summaries
- Generates human-readable content from structured metadata

**Impact:**
- Cannot scale to 500 tables without proper document building
- Vector DB would explode with raw logs/metrics
- Missing the critical transformation layer

**Required Implementation:**
```python
# Example: Logs Summary Builder
def build_logs_weekly_doc(raw_logs: List[LogEntry]) -> RagDocument:
    summary = aggregate_logs(raw_logs)  # Aggregate millions of lines
    content = f"Kafka ingestion experienced {summary.timeout_count} timeouts..."
    return RagDocument(
        source_type="logs_weekly",
        component="Kafka",
        content=content,
        metadata={"timeout_count": summary.timeout_count, ...}
    )
```

### 2. **Component-Specific Lineage Support**

**Current State:**
- Single `LINEAGE` source_type
- No distinction between Kafka, Spark, Cassandra, DataAPI lineage

**New Specification Requires:**
- Separate `source_type` values for each component:
  - `lineage_kafka`
  - `lineage_spark`
  - `lineage_cassandra`
  - `lineage_dataapi`

**Impact:**
- Cannot query lineage for specific components
- "Which Kafka topic feeds this table?" cannot be answered precisely
- Missing granularity for component-specific queries

**Required Fix:**
- Add component-specific lineage types to intent detection
- Update keyword mappings to route to appropriate lineage type
- Add component detection logic (e.g., "kafka topic" → `lineage_kafka`)

### 3. **Time Scope Detection**

**Current State:**
- No automatic detection of time scope in questions
- Uses default 7-day window for all queries

**New Specification Requires:**
- Detect time scope from user questions:
  - "today", "yesterday" → `*_daily` types
  - "this week", "last 7 days" → `*_weekly` types
  - "now", "currently" → `*_daily` types

**Impact:**
- "Why is dda_transactions slow today?" queries weekly summaries instead of daily
- Missing precision for time-sensitive queries

**Required Fix:**
- Add time scope detection to intent service
- Map time keywords to appropriate `source_type` values
- Update query routing logic

---

## 📋 Questions for Clarification

### 1. **Migration Strategy**
- **Question:** Do you have existing data in the vector DB using the old `source_type` values (METADATA, LINEAGE, etc.)?
- **Impact:** Determines if we need backward compatibility or can do a clean migration

### 2. **doc_sub_type Future Use**
- **Question:** In the new architecture, what is the purpose of `doc_sub_type`?
- **Current:** Used for fine-grained filtering under METADATA
- **New Spec:** Not mentioned - should it be deprecated or repurposed?

### 3. **Data Loading Pipeline**
- **Question:** Do you have the semantic document builder implemented, or is this part of the work needed?
- **Impact:** Critical for scaling to 500 tables - need to aggregate logs/metrics before loading

### 4. **Component Detection**
- **Question:** How should we detect which component a question refers to?
- **Example:** "Which Kafka topic feeds this table?" → should route to `lineage_kafka`
- **Current:** No component-specific routing

### 5. **Time Scope Detection**
- **Question:** What keywords should trigger daily vs. weekly document selection?
- **Examples:** "today", "yesterday" → daily; "this week", "last 7 days" → weekly
- **Current:** No time scope detection

---

## 🎯 Recommendations

### Priority 1: 🚨 **ARCHITECTURAL MIGRATION - source_type Values**

**Critical:** Migrate from 2-level typing to granular `source_type` values.

**Steps:**
1. **Update Intent Detection Service:**
   - Replace high-level `source_type` values (METADATA, LINEAGE, etc.) with granular values
   - Map keywords directly to new `source_type` values:
     - "schema" → `schema_metadata`
     - "domain", "owner" → `business_metadata`
     - "tombstone", "compaction" → `storage_configuration`
     - "kafka topic" → `lineage_kafka`
     - "spark job" → `lineage_spark`
     - "today", "yesterday" → `logs_daily`, `metrics_daily`
     - "this week" → `logs_weekly`, `metrics_weekly`

2. **Update SQL Queries:**
   - Remove `doc_sub_type` filtering (or repurpose it)
   - Use `source_type` directly for all filtering
   - Update repository methods to use new `source_type` values

3. **Data Migration:**
   - If existing data exists, create migration script to convert:
     - `source_type='METADATA' AND doc_sub_type='schema_metadata'` → `source_type='schema_metadata'`
     - `source_type='METADATA' AND doc_sub_type='business_metadata'` → `source_type='business_metadata'`
     - `source_type='LINEAGE'` → `source_type='lineage_cassandra'` (or appropriate component)

### Priority 2: **Add Missing source_type Values**

**Add support for:**
- `domain_metadata` (Platform-level)
- `table_statistics` (Row count, size)
- Component-specific lineage: `lineage_kafka`, `lineage_spark`, `lineage_cassandra`, `lineage_dataapi`
- Time-scoped logs: `logs_daily`, `logs_weekly`
- Time-scoped metrics: `metrics_daily`, `metrics_weekly`

### Priority 3: **Implement Semantic Document Builder**

**Critical for scaling to 500 tables:**
- Build aggregation layer for logs (millions of lines → summary documents)
- Build aggregation layer for metrics (time-series → daily/weekly summaries)
- Transform raw metadata into semantic documents
- Generate human-readable content from structured data

### Priority 4: **Add Time Scope Detection**

**Enhance intent detection:**
- Detect time keywords: "today", "yesterday", "this week", "last 7 days"
- Route to appropriate `source_type`:
  - "today" → `logs_daily`, `metrics_daily`
  - "this week" → `logs_weekly`, `metrics_weekly`

### Priority 5: **Add Component Detection**

**Enhance intent detection:**
- Detect component keywords: "kafka", "spark", "cassandra", "api"
- Route to component-specific `source_type`:
  - "kafka topic" → `lineage_kafka`
  - "spark job" → `lineage_spark`

---

## 📝 Implementation Notes

### Current Flow (2-Level Typing)
```
User Question
  ↓
IntentDetectionService.detectIntents() → List<String> sourceTypes
  [Returns: METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY]
  ↓
IntentDetectionService.detectDocSubType() → String docSubType (only for METADATA)
  [Returns: schema_metadata, business_metadata, storage_configuration, data_lifecycle]
  ↓
VectorSearchService.searchVectors() → For each sourceType:
  ↓
RagDocumentRepository.findSimilarDocumentsBySourceType()
  ↓
SQL: WHERE source_type = :sourceType AND (:docSubType IS NULL OR doc_sub_type = :docSubType)
```

### New Specification Flow (1-Level Granular Typing)
```
User Question
  ↓
IntentDetectionService.detectIntents() → List<String> sourceTypes
  [Returns: schema_metadata, business_metadata, storage_configuration, 
           lineage_kafka, lineage_spark, logs_daily, metrics_daily, etc.]
  ↓
Time Scope Detection → daily vs. weekly
  ↓
Component Detection → kafka, spark, cassandra, dataapi
  ↓
VectorSearchService.searchVectors() → For each sourceType:
  ↓
RagDocumentRepository.findSimilarDocumentsBySourceType()
  ↓
SQL: WHERE source_type = :sourceType
  [No doc_sub_type filtering needed - source_type is granular enough]
```

### Data Loading Flow (New Specification)
```
Raw Data Sources
  ↓
Semantic Document Builder
  - Aggregates logs → logs_daily, logs_weekly
  - Aggregates metrics → metrics_daily, metrics_weekly
  - Transforms metadata → business_metadata, schema_metadata, etc.
  - Extracts lineage → lineage_kafka, lineage_spark, etc.
  ↓
Embedding Generator (MiniLM)
  ↓
Vector Loader (Yugabyte)
  ↓
rag_documents table
  [Each row = 1 atomic, intention-aware document]
```

---

## 📅 Next Steps

### Phase 1: Architectural Migration (CRITICAL)
1. **Update source_type Values:**
   - Replace all high-level `source_type` values with granular values
   - Update `IntentDetectionService` keyword mappings
   - Update all SQL queries in repository

2. **Data Migration (if needed):**
   - Create migration script to convert existing data
   - Test migration on sample data
   - Validate data integrity after migration

### Phase 2: Add Missing Features
3. **Add Missing source_type Values:**
   - Add `domain_metadata`, `table_statistics`
   - Add component-specific lineage types
   - Add time-scoped log/metric types

4. **Implement Time Scope Detection:**
   - Add time keyword detection
   - Route to daily vs. weekly document types
   - Update query logic

5. **Implement Component Detection:**
   - Add component keyword detection
   - Route to component-specific lineage types
   - Update query logic

### Phase 3: Semantic Document Builder
6. **Build Aggregation Layer:**
   - Logs aggregation (millions → summaries)
   - Metrics aggregation (time-series → daily/weekly)
   - Metadata transformation

7. **Content Generation:**
   - Generate human-readable content from structured data
   - Create semantic summaries
   - Ensure atomic, intention-aware documents

### Phase 4: Testing & Validation
8. **Test Each Intent:**
   - Schema queries → `schema_metadata`
   - Domain queries → `business_metadata`
   - Tombstones queries → `storage_configuration`
   - Slowness queries → `logs_daily`, `metrics_daily`
   - RCA queries → Multiple types

9. **Validate SQL Queries:**
   - Verify WHERE clauses match new specification
   - Test with sample data
   - Validate results match expected behavior

10. **Scale Testing:**
    - Test with 500 tables worth of documents
    - Verify query performance
    - Validate document grouping

---

## 📊 Summary

### Current State
- ✅ Basic RAG infrastructure working
- ✅ Vector search functional
- ❌ **Architectural mismatch** - uses 2-level typing (source_type + doc_sub_type)
- ❌ Missing granular source_type values
- ❌ No semantic document builder
- ❌ No time scope detection
- ❌ No component-specific routing

### Target State (New Specification)
- ✅ Granular source_type values (direct document purposes)
- ✅ 7 document groups (A-G) with specific source_type values
- ✅ ~15-20 documents per table
- ✅ Semantic document builder for aggregation
- ✅ Time-scoped documents (daily/weekly)
- ✅ Component-specific lineage types
- ✅ Atomic, intention-aware documents

### Migration Complexity
- **High** - Requires complete refactoring of intent detection
- **High** - Requires SQL query updates
- **Medium** - May require data migration
- **High** - Requires semantic document builder implementation

---

**Document Created:** 2025-12-05  
**Last Updated:** 2025-12-05  
**Status:** Analysis Complete - Major Architectural Mismatch Identified - Migration Required

