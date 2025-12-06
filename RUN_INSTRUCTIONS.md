# Run Instructions for Restricted Environment

## Backend (Spring Boot JAR)

### Location
`mvp/backend/target/rag-api-1.0.0.jar`

### Run Command
```bash
cd mvp/backend/target
java -jar rag-api-1.0.0.jar
```

### With Environment Variables
```bash
export DB_NAME=postgres
export DB_USER=yugabyte
export DB_PASSWORD=yugabyte
export DB_HOST=localhost
export DB_PORT=5433
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/generate
export RAG_API_URL=http://localhost:8083/api/rag

java -jar rag-api-1.0.0.jar
```

### Verify
```bash
curl http://localhost:8080/api/rag/health
```

---

## Frontend (React with node_modules)

### Location
`mvp/frontend/` (includes node_modules folder)

### Run Command
```bash
cd mvp/frontend
npm start
```

### Or if npm is not available, use pre-built:
The `node_modules` folder is already included. Just run:
```bash
cd mvp/frontend
./node_modules/.bin/react-scripts start
```

### Verify
Open http://localhost:3000

---

## Quick Start (Both Services)

### Terminal 1 - Backend
```bash
cd mvp/backend/target
export DB_NAME=postgres
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/generate
export RAG_API_URL=http://localhost:8083/api/rag
java -jar rag-api-1.0.0.jar
```

### Terminal 2 - Frontend
```bash
cd mvp/frontend
npm start
```

---

## Notes
- Backend JAR is self-contained (includes all dependencies)
- Frontend node_modules is included (no npm install needed)
- Both can run directly in restricted environment

