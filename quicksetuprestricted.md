# Quick Setup Guide - Restricted Environment

## Prerequisites
- Docker installed and running
- Java 17+ installed
- Node.js 16+ and npm installed
- PostgreSQL client (`psql`) installed

---

## Step 1: Start Phi-4 Q3 Docker Container

```bash
# Pull and start Phi-4 Q3 container
docker pull sakthipsgit/phi4-rag-combined-q3:latest
docker run -d --name phi4-rag-api-q3 -p 8083:5000 sakthipsgit/phi4-rag-combined-q3:latest
```

**Note**: Container runs on port 5000 internally, mapped to 8083 externally.

# Validate container is running
docker ps | grep phi4-rag-api-q3

# Check container logs to verify it loaded properly
docker logs phi4-rag-api-q3

# Or follow logs in real-time
docker logs -f phi4-rag-api-q3

# Test embedding endpoint (after correct port mapping)
curl http://localhost:8083/api/embed -X POST -H "Content-Type: application/json" -d '{"text":"test"}'
```

**If you get "Connection reset" error**: The container runs on port 5000 internally. Restart with correct mapping:
```bash
docker stop phi4-rag-api-q3
docker rm phi4-rag-api-q3
docker run -d --name phi4-rag-api-q3 -p 8083:5000 sakthipsgit/phi4-rag-combined-q3:latest
```
```

**Expected Logs**: Look for:
- Model loading messages
- "Server started" or "Listening on port 8083"
- No error messages

**Expected API Response**: Returns JSON with `"status": "success"` and embedding array.

---

## Step 2: Start YugabyteDB Docker Container

### For MacBook Pro (macOS):
```bash
# Pull and start YugabyteDB container
docker pull yugabyte/yugabyte:latest
docker run -d --name yugabyte -p 5433:5433 -p 7000:7000 -p 9000:9000 yugabyte/yugabyte:latest yugabytedb --daemon=false
```

### For Linux RedHat:
```bash
# Pull and start YugabyteDB container (same command works on Linux)
docker pull yugabyte/yugabyte:latest
docker run -d --name yugabyte -p 5433:5433 -p 7000:7000 -p 9000:9000 yugabyte/yugabyte:latest yugabytedb --daemon=false
```

**Note**: The same Docker image works on both macOS and Linux. Docker handles platform differences automatically.

### Troubleshooting TLS Handshake Timeout

If you get `TLS handshake timeout` error:

**Option 1: Increase Docker timeout and retry**
```bash
# Set Docker daemon timeout (if you have access to Docker daemon config)
# Or retry with longer timeout
docker pull yugabyte/yugabyte:latest --platform linux/amd64
```

**Option 2: Use specific version (if available locally)**
```bash
# Try a different version that might be cached
docker pull yugabyte/yugabyte:2.23.0.0-b1
```

**Option 3: Load from saved image (if you have the image file)**
```bash
# If you have the image saved as a tar file
docker load -i yugabyte-image.tar
```

**Option 4: Check network/proxy settings**
```bash
# Check if Docker can reach registry
docker info | grep -i registry

# If behind proxy, configure Docker proxy settings
# Create/edit: /etc/docker/daemon.json (Linux) or ~/.docker/config.json (macOS)
```

**Option 5: Pre-download on machine with internet, then transfer**
```bash
# On machine with internet:
docker pull yugabyte/yugabyte:latest
docker save yugabyte/yugabyte:latest -o yugabyte-image.tar

# Transfer yugabyte-image.tar to restricted environment, then:
docker load -i yugabyte-image.tar
docker run -d --name yugabyte -p 5433:5433 -p 7000:7000 -p 9000:9000 yugabyte/yugabyte:latest yugabytedb --daemon=false
```

# Wait 10 seconds for startup
sleep 10

# Validate container is running
docker ps | grep yugabyte

# Test connection
psql -h localhost -p 5433 -U yugabyte -d yugabyte -c "SELECT version();"
```

**Expected**: Returns YugabyteDB version information.

---

## Step 3: Setup Database, Tables, and Indexes

```bash
# Create database
psql -h localhost -p 5433 -U yugabyte -d yugabyte -f sql/00_create_database.sql

# Enable vector extension and create schema
psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -f sql/02_create_schema_hnsw_384.sql

# Verify table exists
psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -c "\d rag_documents"
```

**Expected**: Table `rag_documents` created with vector column and HNSW index.

---

## Step 4: Start Backend (JAR)

```bash
# Option 1: Using run script (recommended)
./run-backend.sh

# Option 2: Direct JAR execution
cd backend/target
java -jar rag-api-1.0.0.jar
```

**Expected**: Spring Boot starts on port 8080. Check logs for:
- `Started RagApiApplication`
- `Loaded 12 intent rules from rag-intents.json`
- `Loaded 12 query rewrite templates`

**Validate**: `curl http://localhost:8080/api/rag/health`

---

## Step 5: Start Frontend (NO Internet Required)

```bash
# Run from mvp directory - serves static build files
./run-frontend.sh
```

**Note**: This uses pre-built static files from `frontend/build/` directory. No npm or internet connection needed.

**If build directory doesn't exist**: You need to build it once (requires internet):
```bash
cd frontend
npm install
npm run build
cd ..
./run-frontend.sh
```

**Expected**: React app starts on port 3000. Open `http://localhost:3000` in browser.

---

## Step 6: Load Data into Yugabyte

### Install Python Dependencies First

**If you have internet:**
```bash
pip3 install -r requirements.txt
```

**If you're in restricted environment (no internet) - MacBook Pro:**

**Step 1: Download wheel files (on machine WITH internet)**
```bash
# Run the download script
./download-python-deps.sh

# This creates python-packages/ directory with all wheel files
# Transfer this directory to your restricted MacBook Pro
```

**Step 2: Install from wheel files (on restricted MacBook Pro)**

**Option A: Using virtual environment (recommended)**
```bash
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
source venv/bin/activate

# Install from wheel files
./install-python-deps-offline.sh

# Or manually:
pip install --no-index --find-links python-packages/ -r requirements.txt
```

**Option B: System-wide installation**
```bash
# Install directly (no venv)
./install-python-deps-offline.sh

# Or manually:
pip3 install --no-index --find-links python-packages/ -r requirements.txt
```

**Note**: The script will prompt you to create a venv if one doesn't exist.

**Manual download (if script doesn't work):**
```bash
# On machine with internet, download for macOS:
pip3 download \
    --platform macosx_10_9_universal2 \
    --platform macosx_11_0_arm64 \
    --platform macosx_11_0_x86_64 \
    --only-binary=:all: \
    -r requirements.txt \
    -d python-packages/

# Transfer python-packages/ to restricted MacBook Pro, then:
pip3 install --no-index --find-links python-packages/ -r requirements.txt
```

**Verify installation:**
```bash
python3 -c "import requests; print('requests OK')"
python3 -c "import psycopg2; print('psycopg2 OK')"
```

### Load Data

**If using virtual environment:**
```bash
# Activate venv first
source venv/bin/activate

# Load all 12 canonical document types
python scripts/load_canonical_documents.py

# Or load individually
python scripts/load_canonical_documents.py --file data/02_schema_metadata.json
```

**If not using venv:**
```bash
# Load all 12 canonical document types
python3 scripts/load_canonical_documents.py

# Or load individually
python3 scripts/load_canonical_documents.py --file data/02_schema_metadata.json
```

**Expected**: Documents loaded. Verify:
```bash
psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -c "SELECT source_type, doc_sub_type, COUNT(*) FROM rag_documents GROUP BY source_type, doc_sub_type;"
```

**Expected**: 12 rows (one per doc_sub_type).

---

## Step 7: Test via UI

1. Open `http://localhost:3000`
2. Enter question: `"What is the schema of dda_transactions?"`
3. Click "Ask"
4. Verify answer appears with source documents

---

## Step 8: Gather Logs for All 11 Steps

### Backend Logs Location
- Default: `backend/logs/rag-api.log`
- Console: Output from `java -jar` command

### Log Entries for Each Step

**Step 1-2**: User Question & POST Request
```
INFO c.y.rag.controller.RagController - Received /api/rag/ask request: question=...
```

**Step 3**: Intent Detection
```
INFO c.y.rag.service.IntentDetectionService - Detected intents for question '...': [METADATA]
```

**Step 4**: Query Rewriting
```
INFO c.y.rag.service.VectorSearchService - 🔵 Step 4️⃣: Query Rewriting - STARTED
INFO c.y.rag.service.QueryRewriteService - Query rewritten: '...' → '...'
```

**Step 5**: Embedding Generation
```
INFO c.y.rag.service.VectorSearchService - 🔵 Step 5️⃣: Embedding Generation - STARTED
INFO c.y.rag.service.VectorSearchService - Embedding dimension: 384
```

**Step 6**: Vector Search
```
INFO c.y.rag.service.VectorSearchService - 🔵 Step 6️⃣: Vector Search - STARTED
INFO c.y.rag.service.VectorSearchService - Found X documents for source_type=...
```

**Step 7**: Candidate Selection
```
INFO c.y.rag.service.VectorSearchService - Retrieved X documents, Y passed similarity threshold
```

**Step 8**: Prompt Construction
```
INFO c.y.rag.service.PromptBuilderService - 🔵 Step 8️⃣: Prompt Construction - STARTED
INFO c.y.rag.service.PromptBuilderService - Building prompt with X documents
```

**Step 9**: LLM Generation
```
INFO c.y.rag.service.VectorSearchService - 🔵 Step 9️⃣: Phi-4 LLM Generation - STARTED
INFO c.y.rag.service.VectorSearchService - ✅ Step 9️⃣: Phi-4 LLM Generation - COMPLETED
```

**Step 10-11**: Answer Return & UI Display
- Check browser network tab for response
- Check backend logs for final answer

### Extract All Steps from Logs

```bash
# Extract all step logs for a request
grep -E "Step [0-9]|Received.*request|Detected intents|Query rewritten|Embedding|Found.*documents|Prompt Construction|LLM Generation" backend/logs/rag-api.log | tail -50
```

---

## Troubleshooting

### Phi-4 Container Not Responding
```bash
# Check logs to see what's wrong
docker logs phi4-rag-api-q3

# Check last 50 lines of logs
docker logs phi4-rag-api-q3 --tail 50

# Follow logs in real-time
docker logs -f phi4-rag-api-q3

# Restart container
docker restart phi4-rag-api-q3

# Check if container is running
docker ps -a | grep phi4-rag-api-q3
```

### Yugabyte Connection Failed
```bash
docker logs yugabyte
docker restart yugabyte
sleep 15
```

### Backend Fails to Start
- Check Java version: `java -version` (must be 17+)
- Check port 8080 is free: `lsof -i :8080`
- Check database connection in `application.yml`

### Frontend Fails to Start
- Check Node version: `node -v` (must be 16+)
- Reinstall dependencies: `cd frontend && rm -rf node_modules && npm install`
- Check port 3000 is free: `lsof -i :3000`

### No Documents Retrieved
- Verify data loaded: `psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -c "SELECT COUNT(*) FROM rag_documents;"`
- Check embeddings exist: `psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -c "SELECT id, array_length(embedding::float[], 1) as dim FROM rag_documents LIMIT 1;"`

---

## Quick Validation Commands

```bash
# All services running
docker ps | grep -E "phi4|yugabyte"
curl http://localhost:8083/api/embed -X POST -H "Content-Type: application/json" -d '{"text":"test"}' | jq .status
psql -h localhost -p 5433 -U yugabyte -d rag_llm_optimized -c "SELECT COUNT(*) FROM rag_documents;"
curl http://localhost:8080/api/rag/health
curl http://localhost:3000
```

---

## Environment Variables (Optional)

Create `config/config.env`:
```bash
export RAG_PHI4_URL=http://localhost:8083
export RAG_DB_HOST=localhost
export RAG_DB_PORT=5433
export RAG_DB_NAME=rag_llm_optimized
export RAG_DB_USER=yugabyte
```

Load before starting backend:
```bash
source config/config.env
java -jar backend/target/rag-api-1.0.0.jar
```

