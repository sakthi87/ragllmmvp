# Spring Boot RAG API

Spring Boot REST API for RAG (Retrieval Augmented Generation) MVP.

## Features

- ✅ `/api/rag/query` - Main chat endpoint
- ✅ `/api/rag/search` - Debug retrieval endpoint
- ✅ `/api/rag/ingest` - Load documents into vector DB
- ✅ `/api/rag/health` - System health check

## Prerequisites

- Java 17+
- Maven 3.6+
- YugabyteDB with PGVector extension
- Phi-4 API running (port 8082)

## Configuration

Edit `src/main/resources/application.yml` or set environment variables:

```bash
export DB_HOST=localhost
export DB_PORT=5433
export DB_NAME=yugabyte
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
export EMBED_API_URL=http://localhost:8082/api/embed
export GENERATE_API_URL=http://localhost:8082/api/generate
export RAG_API_URL=http://localhost:8082/api/rag
```

## Build and Run

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Or run JAR
java -jar target/rag-api-1.0.0.jar
```

## API Endpoints

### POST /api/rag/query

Main RAG query endpoint.

**Request:**
```json
{
  "question": "Why was dda_transactions delayed yesterday?",
  "keyspace": "transaction_keyspace",
  "table": "dda_transactions",
  "timeRange": "7d",
  "topK": 6,
  "temperature": 0.3,
  "maxTokens": 200
}
```

**Response:**
```json
{
  "answer": "The delay occurred due to...",
  "confidence": 0.92,
  "mode": "RCA",
  "sources": [...],
  "retrievalTimeMs": 150,
  "generationTimeMs": 3500
}
```

### POST /api/rag/search

Debug retrieval (no LLM generation).

**Request:**
```json
{
  "question": "Which API reads dda_transactions?",
  "table": "dda_transactions",
  "topK": 5
}
```

### POST /api/rag/ingest

Ingest new document.

**Request:**
```json
{
  "sourceType": "METADATA",
  "component": "Cassandra",
  "sourceName": "transaction_keyspace.dda_transactions",
  "keyspace": "transaction_keyspace",
  "tableName": "dda_transactions",
  "content": "This table stores...",
  "metadata": {"ttl_days": 90}
}
```

### GET /api/rag/health

Health check.

**Response:**
```json
{
  "spring": "UP",
  "phi4": "UP",
  "yugabyte": "UP",
  "vector_index": "READY"
}
```

## Architecture

- **Controller**: REST endpoints
- **Service**: Business logic, RAG orchestration
- **Repository**: Yugabyte data access
- **Client**: Phi-4 API integration
- **PromptEngine**: Mode detection and prompt building

## Modes

The API automatically detects query mode:
- **METADATA**: Schema, TTL, ownership questions
- **LINEAGE**: Pipeline, data flow questions
- **LOGS**: Error, failure questions
- **METRICS**: Performance, latency questions
- **RCA**: Root cause analysis questions

