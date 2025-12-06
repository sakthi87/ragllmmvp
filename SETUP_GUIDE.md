# Complete Setup Guide - RAG MVP

## Overview

This MVP includes:
1. **Yugabyte PGVector** - Vector database
2. **Phi-4 Q4 API** - Embeddings and text generation
3. **Spring Boot API** - RAG orchestration
4. **React Chat UI** - User interface

## Prerequisites

- Docker (for Phi-4)
- YugabyteDB running
- Java 17+
- Maven 3.6+
- Node.js 16+

## Step 1: Start Phi-4 API

```bash
docker run -d --name phi4-rag-api-q4 -p 8082:5000 \
  --restart unless-stopped \
  sakthipsgit/phi4-rag-combined-q4:latest
```

Wait 20-30 seconds for models to load, then verify:

```bash
curl http://localhost:8082/health
```

## Step 2: Setup Yugabyte Database

```bash
# Connect to Yugabyte
psql -h localhost -p 5433 -U yugabyte -d yugabyte

# Create schema
\i sql/01_create_schema.sql
```

## Step 3: Load Initial Data

```bash
cd mvp
pip install -r requirements.txt

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5433
export DB_NAME=yugabyte
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
export EMBED_API_URL=http://localhost:8082/api/embed

# Load data
python scripts/generate_embeddings.py
```

## Step 4: Start Spring Boot API

```bash
cd backend

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5433
export DB_NAME=yugabyte
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
export EMBED_API_URL=http://localhost:8082/api/embed
export GENERATE_API_URL=http://localhost:8082/api/generate
export RAG_API_URL=http://localhost:8082/api/rag

# Run
mvn spring-boot:run
```

API will be available at http://localhost:8080/api

## Step 5: Start React UI

```bash
cd frontend

# Create .env file
echo "REACT_APP_API_URL=http://localhost:8080/api" > .env

# Install and run
npm install
npm start
```

UI will open at http://localhost:3000

## Step 6: Test

1. Open http://localhost:3000
2. Try these questions:
   - "What is the schema of dda_transactions?"
   - "Why was dda_transactions delayed yesterday?"
   - "Which API reads from this table?"
   - "What caused Kafka lag?"

## Troubleshooting

### Phi-4 API not responding
```bash
docker logs phi4-rag-api-q4
docker restart phi4-rag-api-q4
```

### Database connection issues
```bash
psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "SELECT 1"
```

### Spring Boot errors
Check logs in console. Verify all environment variables are set.

### React UI can't connect
- Check API is running: `curl http://localhost:8080/api/rag/health`
- Verify `.env` file has correct API URL
- Check browser console for errors

## Architecture

```
React UI (port 3000)
    ↓
Spring Boot API (port 8080)
    ↓
    ├─→ Yugabyte PGVector (port 5433)
    └─→ Phi-4 API (port 8082)
```

## Next Steps

- Add authentication
- Implement streaming responses
- Add chat history
- Deploy to production

