# ✅ Complete RAG MVP - Ready to Run!

## 📁 Project Structure

```
mvp/
├── backend/                    # Spring Boot API
│   ├── pom.xml
│   ├── src/main/java/com/yugabyte/rag/
│   │   ├── RagApiApplication.java
│   │   ├── controller/RagController.java
│   │   ├── service/RagService.java
│   │   ├── service/PromptEngine.java
│   │   ├── repository/RagDocumentRepository.java
│   │   ├── client/Phi4Client.java
│   │   └── model/ (all request/response models)
│   └── src/main/resources/application.yml
│
├── frontend/                   # React Chat UI
│   ├── package.json
│   ├── public/index.html
│   └── src/
│       ├── App.js
│       ├── App.css
│       ├── index.js
│       └── index.css
│
├── sql/                        # Database schema
│   └── 01_create_schema.sql
│
├── data/                       # Sample data
│   ├── metadata.json
│   ├── lineage.json
│   └── logs_metrics_7days.json
│
├── scripts/                    # Python utilities
│   ├── generate_embeddings.py
│   └── test_rag_query.py
│
└── config/                     # Configuration
    └── config.env.example
```

## 🚀 Quick Start (5 Steps)

### 1. Start Phi-4 API
```bash
docker run -d --name phi4-rag-api-q4 -p 8082:5000 \
  --restart unless-stopped \
  sakthipsgit/phi4-rag-combined-q4:latest

# Wait 30 seconds, then verify
curl http://localhost:8082/health
```

### 2. Setup Database
```bash
psql -h localhost -p 5433 -U yugabyte -d yugabyte -f sql/01_create_schema.sql
```

### 3. Load Data
```bash
cd mvp
pip install -r requirements.txt
export EMBED_API_URL=http://localhost:8082/api/embed
python scripts/generate_embeddings.py
```

### 4. Start Spring Boot API
```bash
cd backend
export DB_HOST=localhost
export DB_PORT=5433
export EMBED_API_URL=http://localhost:8082/api/embed
export GENERATE_API_URL=http://localhost:8082/api/generate
export RAG_API_URL=http://localhost:8082/api/rag
mvn spring-boot:run
```

### 5. Start React UI
```bash
cd frontend
echo "REACT_APP_API_URL=http://localhost:8080/api" > .env
npm install
npm start
```

## 🎯 Test Questions

Open http://localhost:3000 and try:

1. **Metadata**: "What is the schema of dda_transactions?"
2. **Lineage**: "Which API reads from this table?"
3. **RCA**: "Why was dda_transactions delayed yesterday?"
4. **Metrics**: "What was yesterday's Cassandra latency?"

## 📊 API Endpoints

- `POST /api/rag/query` - Main chat endpoint
- `POST /api/rag/search` - Debug retrieval
- `POST /api/rag/ingest` - Load documents
- `GET /api/rag/health` - Health check

## ✨ Features

✅ ChatGPT-like UI  
✅ Automatic mode detection (METADATA, LINEAGE, LOGS, METRICS, RCA)  
✅ Source citations with similarity scores  
✅ Confidence scoring  
✅ Performance metrics  
✅ Error handling  

## 🔧 Configuration

All configuration via environment variables or `application.yml`.

See `SETUP_GUIDE.md` for detailed instructions.

