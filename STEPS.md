# 10 Steps to Test RAG MVP

## Prerequisites
- ✅ Phi-4 Q3 container running on port 8083
- ✅ YugabyteDB running on port 5433

---

## Step 1: Verify Phi-4 is Ready
```bash
curl http://localhost:8083/health
```
Should return: `{"status":"healthy"}`

---

## Step 2: Create Database Schema (in pgAdmin)
Connect to `postgres` database and run:
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type TEXT,
    component TEXT,
    source_name TEXT,
    keyspace TEXT,
    table_name TEXT,
    domain TEXT,
    sub_domain TEXT,
    event_date DATE,
    time_window TEXT,
    content TEXT,
    metadata JSONB,
    embedding vector(384),
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_rag_embedding_ivf ON rag_documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_rag_keyspace_table ON rag_documents(keyspace, table_name);
```

---

## Step 3: Load Data
```bash
cd mvp
export EMBED_API_URL=http://localhost:8083/api/embed
export DB_NAME=postgres
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
python3 scripts/generate_embeddings.py
```
Wait for: "Successfully loaded: 30"

---

## Step 4: Verify Data Loaded
In pgAdmin:
```sql
SELECT COUNT(*) FROM rag_documents;
-- Should return 30
```

---

## Step 5: Update Backend Config
Edit `mvp/backend/src/main/resources/application.yml`:
- Change `DB_NAME:yugabyte` to `DB_NAME:postgres`
- Change Phi-4 URLs to port `8083` (if not already)

---

## Step 6: Build Backend
```bash
cd mvp/backend
mvn clean package -DskipTests
```

---

## Step 7: Start Backend
```bash
cd mvp/backend
mvn spring-boot:run
```
Wait for: "Started RagApiApplication"

---

## Step 8: Test Backend API
In new terminal:
```bash
curl http://localhost:8080/api/rag/health
```
Should return all services UP.

---

## Step 9: Start Frontend
In new terminal:
```bash
cd mvp/frontend
echo "REACT_APP_API_URL=http://localhost:8080/api" > .env
npm install
npm start
```

---

## Step 10: Test in Browser
1. Open http://localhost:3000
2. Try: "What is the schema of dda_transactions?"
3. Wait 1-2 minutes for response (CPU is slow)

---

## Troubleshooting
- Backend timeout? Increase `phi4.timeout: 600000` in application.yml
- Frontend error? Check browser console (F12)
- No data? Verify Step 3 completed successfully

