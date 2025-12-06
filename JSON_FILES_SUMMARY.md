# JSON Files Added as Part of New Revisions/Improvements

## 📋 Summary

As part of the production-grade improvements (query rewriting, per-doc-type thresholds, example_questions matching), the following JSON configuration files were added:

---

## ✅ Backend Configuration Files (Spring Boot Resources)

### 1. `rag-intents.json`
**Location:** `mvp/backend/src/main/resources/rag-intents.json`

**Purpose:** 
- Machine-readable intent detection configuration
- Maps keywords to document types (`doc_sub_type`)
- Defines time windows for time-scoped intents
- Used by `IntentDetectionService` for intent detection

**Contents:**
- 12 intent rules (one per canonical document type)
- Keywords for each intent
- Time window configuration (`time_window_days`)
- Intent names (e.g., `BUSINESS_METADATA`, `SCHEMA_METADATA`)

**Used By:**
- `IntentConfigLoader` - Loads at startup
- `IntentDetectionService` - Uses for keyword-based intent detection

---

### 2. `query-rewrite-templates.json`
**Location:** `mvp/backend/src/main/resources/query-rewrite-templates.json`

**Purpose:**
- Query rewriting templates for canonical query generation
- Per-doc-type similarity thresholds
- Example questions for intent detection
- Source type mapping

**Contents:**
- 12 rewrite templates (one per `doc_sub_type`)
- Each template includes:
  - `doc_sub_type` - Fine-grained document type
  - `source_type` - High-level category (METADATA, LINEAGE, LOG_SUMMARY, METRIC_SUMMARY)
  - `rewrite_template` - Canonical query template with `{keyspace}` and `{table}` placeholders
  - `description` - Human-readable description
  - `example` - Example of rewritten query
  - `similarity_threshold` - Per-doc-type threshold (0.63-0.75)
  - `example_questions` - Array of example questions for intent detection

**Used By:**
- `QueryRewriteConfigLoader` - Loads at startup
- `QueryRewriteService` - Uses for query rewriting
- `VectorSearchService` - Uses for per-doc-type threshold lookup
- `IntentDetectionService` - Uses `example_questions` for better intent matching

**Example Entry:**
```json
{
  "doc_sub_type": "schema_metadata",
  "source_type": "METADATA",
  "rewrite_template": "Schema definition of cassandra table {keyspace}.{table} including primary key, partition key and clustering columns",
  "description": "Retrieve the schema details of a Cassandra table including PK, partition key, and clustering columns.",
  "example": "Schema definition of cassandra table transaction_keyspace.dda_transactions including primary key, partition key and clustering columns",
  "similarity_threshold": 0.75,
  "example_questions": [
    "What is the schema of dda_transactions?",
    "Show the primary and clustering keys for table dda_transactions",
    "Columns and schema details of table dda_transactions"
  ]
}
```

---

## ✅ Data Files (Canonical Documents)

### 3-14. 12 Canonical Document JSON Files
**Location:** `mvp/data/`

**Files:**
1. `01_business_metadata.json`
2. `02_schema_metadata.json`
3. `03_storage_configuration.json`
4. `04_table_statistics.json`
5. `05_data_lifecycle.json`
6. `06_lineage_kafka.json`
7. `07_lineage_spark.json`
8. `08_lineage_dataapi.json`
9. `09_logs_daily.json`
10. `10_logs_weekly.json`
11. `11_metrics_daily.json`
12. `12_metrics_weekly.json`

**Purpose:**
- Sample canonical documents for `transaction_keyspace.dda_transactions` table
- Represents all 12 document types in the production schema
- Used for testing and as examples for data ingestion
- Can be loaded into YugabyteDB using `load_canonical_documents.py`

**Structure:**
Each file contains a JSON object with:
- `cluster_name` - Cluster identifier
- `source_type` - High-level category
- `doc_sub_type` - Fine-grained type (one of the 12 types)
- `entity_type` - Entity type (table, kafka_topic, spark_job, etc.)
- `component` - Component name (Cassandra, Kafka, Spark, DataAPI)
- `source_name` - Canonical source name
- `keyspace` - Cassandra keyspace
- `table_name` - Table name
- `domain` - Business domain
- `sub_domain` - Sub-domain
- `event_date` - Date (for logs/metrics)
- `time_window` - Time window (for logs/metrics)
- `content` - LLM-friendly human-readable content
- `metadata` - Structured metadata (JSON object)

---

## 📊 File Summary

| Category | File Count | Purpose |
|----------|------------|---------|
| **Backend Config** | 2 | Intent detection & query rewriting |
| **Data Files** | 12 | Canonical document examples |
| **Total** | **14** | Configuration & sample data |

---

## 🔄 How These Files Are Used

### Startup Sequence:
1. Spring Boot starts
2. `IntentConfigLoader` loads `rag-intents.json`
3. `QueryRewriteConfigLoader` loads `query-rewrite-templates.json`
4. Both build in-memory maps for fast lookup
5. Services ready to use JSON-based configuration

### Runtime Usage:
1. **Intent Detection:**
   - User question → `IntentDetectionService`
   - Checks `rag-intents.json` keywords
   - Checks `query-rewrite-templates.json` example_questions
   - Returns detected `doc_sub_type`

2. **Query Rewriting:**
   - Detected `doc_sub_type` → `QueryRewriteService`
   - Looks up template in `query-rewrite-templates.json`
   - Replaces `{keyspace}` and `{table}` placeholders
   - Returns canonical query

3. **Similarity Threshold:**
   - Document's `doc_sub_type` → `VectorSearchService`
   - Looks up threshold in `query-rewrite-templates.json`
   - Applies per-doc-type threshold for filtering

---

## ✅ Benefits

1. **Machine-Readable Configuration:**
   - JSON files can be parsed by other tools
   - Easy to version control
   - No code changes needed for updates

2. **Maintainability:**
   - Clear separation of configuration and logic
   - Easy to add new intents or templates
   - Self-documenting structure

3. **Flexibility:**
   - Per-doc-type thresholds configurable
   - Example questions for testing
   - Template-based query rewriting

4. **Production-Ready:**
   - All 12 canonical document types covered
   - Complete configuration for intent detection
   - Ready for scale to 500 tables

---

## 📝 Notes

- **Target Files:** The files in `mvp/backend/target/classes/` are compiled copies (generated by Maven)
- **Source Files:** The actual source files are in `mvp/backend/src/main/resources/`
- **Data Files:** The 12 canonical document files in `mvp/data/` are sample data for testing

---

**Status:** ✅ All JSON files created and integrated into the codebase

