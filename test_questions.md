# RAG MVP Test Questions

## A) METADATA QUESTIONS

- What is the schema of dda_transactions?
- What is the TTL of dda_transactions?
- Which domain does this table belong to?
- Is this a PII table?
- Who owns this dataset?
- What is the primary key of dda_transactions?

## B) LINEAGE QUESTIONS

- How is dda_transactions populated?
- Which Kafka topic feeds this table?
- Which Spark job loads dda_transactions?
- Which API reads from this table?
- What happens if Kafka goes down?
- What is the data flow for dda_transactions?

## C) LOG QUESTIONS

- Were there any Spark failures yesterday?
- Which executor failed most frequently?
- Did API experience any latency spikes?
- When did ingestion slow down?
- What errors occurred in Spark job yesterday?
- How many records were processed yesterday?

## D) METRIC QUESTIONS

- What was yesterday's Cassandra write latency?
- How high did Kafka lag go?
- Did Spark throughput drop?
- What was the worst performance day this week?
- What was the average latency for Cassandra writes?
- What was the peak Kafka consumer lag?

## E) RCA QUESTIONS (🔥 Killer Demo Questions)

- Why was dda_transactions delayed yesterday?
- What caused Kafka lag?
- Did Spark failures impact API performance?
- What was the root cause of yesterday's delay?
- Which component was the bottleneck?
- Why did API latency increase yesterday?
- What caused the Spark job to slow down?
- How did Spark failures affect the pipeline?

