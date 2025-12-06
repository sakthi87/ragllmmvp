# Quick Run Guide

## ✅ Ready Files
- **Backend JAR**: `backend/target/rag-api-1.0.0.jar` (54MB - includes all dependencies)
- **Frontend**: `frontend/` (includes node_modules - 411MB, no npm install needed)

---

## 🚀 Run Backend

### Option 1: Using script
```bash
cd mvp/backend
./run.sh
```

### Option 2: Direct JAR
```bash
cd mvp/backend/target
export DB_NAME=postgres
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/generate
export RAG_API_URL=http://localhost:8083/api/rag
java -jar rag-api-1.0.0.jar
```

**Verify**: `curl http://localhost:8080/api/rag/health`

---

## 🚀 Run Frontend

### Option 1: Using script
```bash
cd mvp/frontend
./run.sh
```

### Option 2: Direct
```bash
cd mvp/frontend
./node_modules/.bin/react-scripts start
```

**Open**: http://localhost:3000

---

## 📝 Notes
- Both are self-contained (no build needed)
- JAR includes all Java dependencies
- node_modules includes all npm packages
- Ready to run in restricted environment

