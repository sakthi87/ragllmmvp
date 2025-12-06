# RAG Platform Roadmap - Items To Do

**Last Updated**: 2025-12-06  
**Status**: Active Planning

---

## ⚡ **P0 — CRITICAL PERFORMANCE (CPU Optimization)**

### 0️⃣ CPU-Only LLM Performance Optimization

**Why:** LLM generation taking 4.4 minutes (264 seconds) is unacceptable for production.

**Completed:**
* ✅ Context window: 2048 → 512 (40-60% faster)
* ✅ Threading: Auto → Explicit (cores-1) (30-50% faster)
* ✅ Memory: use_mmap=True, n_threads_batch (5-15% faster)
* ✅ Code changes committed to `api_server.py`

**Pending:**
* ⚠️ Apply `api_server.py` changes to Docker container
* ⚠️ Update Docker run command with CPU/memory limits (`--cpus="8" --memory="8g"`)

**Expected Impact:** 4-8x faster (264s → 30-60s)

**Priority:** ⚡ P0 (Critical Performance)

**Status:** ✅ Code Complete | ⚠️ Needs Deployment

**Documentation:**
* `CPU_OPTIMIZATION_GUIDE.md` - Detailed implementation guide
* `OPTIMIZATION_STATUS.md` - Current status and deployment steps
* `CPU_OPTIMIZATION_REVIEW.md` - Review of additional recommendations

---

## 🔥 **P0 — CRITICAL (Do These First)**

These directly protect **correctness, safety, and hallucination control** at enterprise scale.

### 1️⃣ Multi-Intent Query Execution & Prompt Fusion

**Why:** Users will ask:

> "What is the schema and today's errors for dda_transactions?"

✅ You already detect multi-intent

❌ You still need:

* Parallel vector searches per `doc_sub_type`
* Merge results into a **single structured prompt**
* Keep strict grouping:

  ```
  [SCHEMA]
  [LOGS_DAILY]
  [METRICS_DAILY]
  ```

**Risk if skipped:** Partial answers, hallucinated joins

**Priority:** 🔥 P0

**Status:** ⏳ Not Started

---

### 2️⃣ Hard Grounding Enforcement at Answer Post-Processing

You already added the **prompt grounding guard**, but the final missing piece is:

✅ Add a **post-response validator**:

* If model answers something **not present in retrieved context**
* → Replace answer with:

  ```
  "This information is not available in the current metadata."
  ```

**Risk if skipped:** Silent hallucinations in prod

**Priority:** 🔥 P0

**Status:** ⏳ Not Started

---

### 3️⃣ Deterministic Table + Entity Resolution

Right now, if user asks:

> "What's the schema of transactions?"

You still need:

* Fuzzy resolution:
  * `transactions` → `transaction_keyspace.dda_transactions`
* Backed by:
  * Alias map
  * Data catalog table

**Risk if skipped:** Wrong table → wrong answer

**Priority:** 🔥 P0

**Status:** ⏳ Not Started

---

## 🚀 **P1 — HIGH ROI (Scale & Performance)**

These protect you as **document volume grows into millions**.

### 4️⃣ Partitioning Strategy for `rag_documents`

You should implement **range partitioning** on:

```sql
PARTITION BY RANGE (event_date)
```

With:

* Monthly partitions for logs & metrics
* Static partition for metadata

**Why:**

* 10x faster vector scans
* Faster purges
* Lower index bloat

**Priority:** 🚀 P1

**Status:** ⏳ Not Started

---

### 5️⃣ Automated Retention & Purge Jobs

You already use:

* `daysBack = 180`

Now you should enforce:

* Auto-delete:

  ```
  logs_daily > 30 days
  logs_weekly > 365 days
  metrics_daily > 90 days
  ```

Using:

* Spring Batch OR Spark job OR Yugabyte cron

**Priority:** 🚀 P1

**Risk if skipped:** Vector index explosion + slow recall

**Status:** ⏳ Not Started

---

### 6️⃣ Hot vs Cold Index Strategy

Split into:

* **Hot HNSW Index**
  * logs_daily
  * metrics_daily

* **Cold HNSW Index**
  * schema_metadata
  * business_metadata
  * lineage

**Why:**

* Faster real-time RCA queries
* No contention between live logs and static metadata

**Priority:** 🚀 P1

**Status:** ⏳ Not Started

---

## 🧠 **P2 — QUALITY & INTELLIGENCE**

These dramatically improve **answer accuracy and observability**.

### 7️⃣ Adaptive Similarity Threshold Learning

You already have:

* Static thresholds per doc type

Next step:

* Log:

  ```
  question → similarity → answer accepted/rejected
  ```

* Train:
  * Auto-adjust thresholds per subtype

**Result:** Higher recall without lowering precision

**Priority:** 🧠 P2

**Status:** ⏳ Not Started

---

### 8️⃣ RAG Answer Quality Scoring (LLM-as-a-Judge)

Add a second LLM call:

* Input: Question + Context + Answer
* Output:
  * Grounded? (Yes/No)
  * Missing fields?
  * Confidence score

Store in:

* `rag_answers_audit` table

**Priority:** 🧠 P2

**This is mandatory for enterprise compliance later.**

**Status:** ⏳ Not Started

---

### 9️⃣ Query Rewriting with LLM (Hybrid Mode)

You currently use:

✅ Rule-based canonical templates

Next evolution:

* If confidence < 0.7:
  * Ask Phi-4 to rewrite into canonical form
  * Then re-embed + retry

This creates a **self-healing RAG pipeline**.

**Priority:** 🧠 P2

**Status:** ⏳ Not Started

---

## 🏗️ **P3 — PLATFORM SCALE & DEV EXPERIENCE**

These help your **platform grow without friction**.

### 🔟 Backfill & Re-Embedding Strategy

When you:

* Upgrade embedding model
* Change canonical templates
* Add new metadata

You need:

* Versioned embeddings:

  ```
  embedding_v1
  embedding_v2
  ```

* Online + offline backfill jobs

**Priority:** 🏗️ P3

**Status:** ⏳ Not Started

---

### 1️⃣1️⃣ Canary RAG Evaluation Harness

Build:

* A fixed test dataset:
  * 100 questions
  * Expected answers
* Run nightly regression:
  * Recall@1
  * Hallucination rate
  * Latency

**Priority:** 🏗️ P3

**Status:** ⏳ Not Started

---

### 1️⃣2️⃣ Multi-Cluster Federated RAG

You already have:

✅ `cluster_name`

Next step:

* Query:
  * Single cluster
  * Or federated across:
    * cassandra-prod
    * cassandra-staging
    * yugabyte-analytics

**Priority:** 🏗️ P3

**Status:** ⏳ Not Started

---

## 🔮 **P4 — ADVANCED AIOPS (Nice-to-Have, High Differentiation)**

### 1️⃣3️⃣ Predictive Incident Detection

Use:

* metrics_weekly trends
* logs_weekly error slopes

Ask:

> "Will dda_transactions likely fail today?"

This is:

* Predictive RAG + ML hybrid

**Priority:** 🔮 P4

**Status:** ⏳ Not Started

---

### 1️⃣4️⃣ Auto-RCA Graph Builder

From:

* logs
* spark lineage
* kafka lineage
* API lineage

Auto-generate:

* Failure propagation graph

**Priority:** 🔮 P4

**This becomes your AppDynamics replacement.**

**Status:** ⏳ Not Started

---

### 1️⃣5️⃣ Natural Language → Operational Action

Allow:

> "Pause the Spark job writing to dda_transactions"

Pipeline:

UI → RAG → Tool Router → Spark API

**Priority:** 🔮 P4

**Status:** ⏳ Not Started

---

## 🗄️ **INFRASTRUCTURE & SCHEMA EVOLUTION**

### 1️⃣6️⃣ Migrate to TIMESTAMPTZ for Hour-Level RCA

**Current State:**
* Using `event_date DATE` with `LocalDate` in Java
* ✅ Valid and correct for day-level granularity
* ✅ Validated with real data

**Why Migrate:**
* Enable hour-level RCA queries
* Support timezone-aware operations
* Better for distributed systems
* Future-proof for predictive analytics

**Migration Plan:**
* Add `event_timestamp TIMESTAMPTZ` column
* Migrate existing `event_date` → `event_timestamp` (set to midnight UTC)
* Update Java models to use `Instant` or `LocalDateTime`
* Update SQL queries to use `event_timestamp`
* Keep `event_date` for backward compatibility (computed column)
* Update date filtering to use `event_timestamp >= :fromTimestamp AND event_timestamp <= :toTimestamp`

**Priority:** 🏗️ P3 (Do before hour-level RCA features)

**Status:** ⏳ Not Started

**Dependencies:**
* Complete P0 items first
* Test with existing data

---

## ✅ FINAL EXECUTION ROADMAP (CONDENSED)

### ⚡ Do Immediately (P0 Performance)

0. **Deploy CPU optimizations to Docker container** (Code ready, needs deployment)

### 🔥 Do Immediately (P0 Critical)

1. Multi-intent vector fusion
2. Post-answer grounding validator
3. Deterministic table/entity resolution

---

### 🚀 Do After Stabilization (P1)

4. Yugabyte partitioning
5. Auto-retention jobs
6. Hot vs cold vector indexes

---

### 🧠 Intelligence Layer (P2)

7. Adaptive similarity thresholds
8. LLM-as-a-judge evaluator
9. LLM-assisted query rewriting fallback

---

### 🏗️ Scale & Ops (P3)

10. Embedding backfill versioning
11. Canary regression suite
12. Federated multi-cluster RAG
13. **Migrate to TIMESTAMPTZ** (for hour-level RCA)

---

### 🔮 AIOps Leadership (P4)

14. Predictive failures
15. Auto-RCA graphs
16. Natural-language ops execution

---

## 📊 Progress Tracking

| Priority | Total Items | Completed | In Progress | Not Started |
|----------|-------------|-----------|-------------|-------------|
| P0 (Performance) | 1 | 0 (Code) | 1 (Deployment) | 0 |
| P0 (Critical) | 3 | 0 | 0 | 3 |
| P1 | 3 | 0 | 0 | 3 |
| P2 | 3 | 0 | 0 | 3 |
| P3 | 4 | 0 | 0 | 4 |
| P4 | 3 | 0 | 0 | 3 |
| **Total**| **17**      | **0**     | **1**       | **16**      |

**Note:** P0 Performance optimization code is complete but needs deployment to Docker container.

---

## 📝 Notes

* All items are prioritized based on impact, risk reduction, and production scalability
* P0 items protect correctness and safety
* P1 items protect scale and performance
* P2 items improve quality and intelligence
* P3 items enable platform growth
* P4 items provide advanced AIOps capabilities

---

**Next Review Date**: TBD

---

## 🎯 Recent Updates (2025-12-06)

### ✅ Completed
* CPU optimization code (P0 Performance) - All code changes complete in `api_server.py`
  * Context window: 2048 → 512
  * Threading: Explicit (cores-1)
  * Memory optimizations: use_mmap=True, n_threads_batch

### ⚠️ In Progress
* CPU optimization deployment - Code ready, needs to be applied to Docker container

### 📝 Documentation Added
* `CPU_OPTIMIZATION_GUIDE.md` - Complete implementation guide
* `OPTIMIZATION_STATUS.md` - Current status and deployment checklist
* `CPU_OPTIMIZATION_REVIEW.md` - Review of additional recommendations
* `APPLY_CPU_OPTIMIZATIONS.md` - Step-by-step deployment instructions

