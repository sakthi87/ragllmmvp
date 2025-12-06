# Questions Answerable by Loaded Data

Based on 30 documents loaded (1 metadata + 1 lineage + 14 logs + 14 metrics)

---

## 📋 METADATA Questions (1 document)

### Table Schema
- What is the schema of dda_transactions?
- What columns does dda_transactions table have?
- What is the primary key of dda_transactions?
- What is the TTL of dda_transactions?
- How long is data retained in dda_transactions?

### Data Ownership
- Which domain does dda_transactions belong to?
- What is the sub-domain of dda_transactions?
- Who owns the dda_transactions dataset?
- Is dda_transactions a PII table?
- What is the data owner for dda_transactions?

---

## 🔗 LINEAGE Questions (1 document)

### Data Flow
- How is dda_transactions populated?
- What is the data pipeline for dda_transactions?
- Which Kafka topic feeds dda_transactions?
- Which Spark job loads dda_transactions?
- Which API reads from dda_transactions?

### Dependencies
- What happens if Kafka goes down?
- What is the upstream source for dda_transactions?
- What are the downstream consumers of dda_transactions?
- What is the complete data flow for dda_transactions?

---

## 📊 LOG Questions (14 documents - 7 days)

### Spark Job Logs (7 documents)
- Were there any Spark failures yesterday?
- Which executor failed most frequently?
- How many OutOfMemoryError events occurred yesterday?
- When did Spark job slow down?
- What errors occurred in Spark job on 2025-11-30?
- How many records were processed by Spark yesterday?
- Which executor had the most errors?
- What was the runtime of Spark job yesterday?
- Did Spark job complete successfully yesterday?

### Data API Logs (7 documents)
- Did API experience any latency spikes?
- When did API latency increase?
- Which API endpoints were affected?
- What was the average API response time yesterday?
- Did AccountTransactionAPI have any issues?
- When did API performance degrade?

---

## 📈 METRIC Questions (14 documents - 7 days)

### Cassandra Metrics (7 documents)
- What was yesterday's Cassandra write latency?
- What was the average Cassandra latency for dda_transactions?
- What was the peak Cassandra latency?
- How does current latency compare to baseline?
- What was the worst Cassandra performance day this week?
- Did Cassandra latency exceed baseline yesterday?

### Kafka Metrics (7 documents)
- How high did Kafka lag go?
- What was the peak Kafka consumer lag?
- What is the baseline Kafka lag?
- Did Kafka lag increase yesterday?
- What was the maximum Kafka lag this week?
- When did Kafka lag spike?

### Performance Trends
- What was the worst performance day this week?
- Did Spark throughput drop?
- Which day had the most performance issues?
- What was the performance trend over the last 7 days?

---

## 🔍 ROOT CAUSE ANALYSIS (RCA) Questions - **KILLER DEMO**

### Yesterday's Issues (2025-11-30)
- Why was dda_transactions delayed yesterday?
- What caused the delay in dda_transactions yesterday?
- What was the root cause of yesterday's delay?
- Why did dda_transactions slow down yesterday?
- What happened to dda_transactions on 2025-11-30?

### Component Analysis
- What caused Kafka lag?
- Why did Kafka consumer lag increase?
- What caused Spark failures?
- Why did Spark job slow down?
- What caused Cassandra latency to increase?
- Why did API latency spike?

### Impact Analysis
- Did Spark failures impact API performance?
- How did Spark issues affect downstream systems?
- What was the impact of Kafka lag?
- Which component was the bottleneck?
- What was the cascading effect of Spark failures?

### Cross-System Analysis
- How did Spark OutOfMemoryError affect Cassandra?
- Did Spark failures cause API latency?
- What was the relationship between Spark errors and Kafka lag?
- How did component failures affect each other?

---

## 📅 TIME-BASED Questions

### Specific Dates
- What happened on 2025-11-30?
- What issues occurred on 2025-11-27?
- What was the performance on 2025-11-24?
- Compare performance between different days

### Time Windows
- What happened in the last 7 days?
- What were the issues this week?
- When did problems start?
- What was the timeline of yesterday's issues?

---

## 🎯 COMPARISON Questions

### Day-to-Day
- How does yesterday compare to previous days?
- Was yesterday worse than other days?
- What was different about yesterday?
- Which day had the most errors?

### Component Comparison
- Which component had more issues: Spark or Kafka?
- Compare Spark performance across days
- Compare Cassandra latency across the week

---

## 💡 SUMMARY Questions

### Overall Status
- What is the current status of dda_transactions pipeline?
- Are there any ongoing issues?
- What is the health of the data pipeline?
- What should I know about dda_transactions?

---

## ✅ Best Demo Questions (High Impact)

1. **"Why was dda_transactions delayed yesterday?"**
   - Will retrieve: Spark logs, Cassandra metrics, Kafka metrics, API logs
   - Will explain: Root cause chain

2. **"What caused Kafka lag?"**
   - Will retrieve: Kafka metrics, Spark logs
   - Will explain: Spark failures → slow consumption → lag

3. **"Which component was the bottleneck yesterday?"**
   - Will retrieve: All metrics and logs
   - Will explain: Spark (32 errors) → cascading effects

4. **"What is the schema of dda_transactions?"**
   - Will retrieve: Metadata document
   - Will explain: Table structure, TTL, ownership

5. **"How is dda_transactions populated?"**
   - Will retrieve: Lineage document
   - Will explain: Kafka → Spark → Cassandra → API flow

---

**Total Documents Available: 30**
- 1 Metadata
- 1 Lineage  
- 14 Logs (7 Spark + 7 DataAPI)
- 14 Metrics (7 Cassandra + 7 Kafka)

