#!/bin/bash
# Run Spring Boot JAR

export DB_NAME=${DB_NAME:-postgres}
export DB_USER=${DB_USER:-yugabyte}
export DB_PASSWORD=${DB_PASSWORD:-yugabyte}
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5433}
export EMBED_API_URL=${EMBED_API_URL:-http://localhost:8083/api/embed}
export GENERATE_API_URL=${GENERATE_API_URL:-http://localhost:8083/api/generate}
export RAG_API_URL=${RAG_API_URL:-http://localhost:8083/api/rag}

cd "$(dirname "$0")/target"
java -jar rag-api-1.0.0.jar

