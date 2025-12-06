#!/bin/bash
# Setup script for rag_llm_optimized database with HNSW 384-dim schema
# This script creates the database and runs the schema creation

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_USER="${DB_USER:-yugabyte}"
DB_PASSWORD="${DB_PASSWORD:-yugabyte}"
NEW_DB="rag_llm_optimized"

echo "=========================================="
echo "Setting up rag_llm_optimized database"
echo "=========================================="
echo "Host: $DB_HOST:$DB_PORT"
echo "User: $DB_USER"
echo "Database: $NEW_DB"
echo ""

# Step 1: Create the database (connect to default postgres database)
echo "Step 1: Creating database '$NEW_DB'..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "CREATE DATABASE $NEW_DB;" 2>&1 | grep -v "already exists" || echo "Database already exists, continuing..."

# Step 2: Run the HNSW schema creation
echo ""
echo "Step 2: Creating schema with HNSW index (384-dim)..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $NEW_DB -f "$(dirname "$0")/02_create_schema_hnsw_384.sql"

echo ""
echo "=========================================="
echo "✅ Database setup complete!"
echo "=========================================="
echo "Database: $NEW_DB"
echo "Schema: rag_documents table with HNSW index (384-dim)"
echo ""
echo "Next steps:"
echo "1. Update your Spring Boot application.yml (already done)"
echo "2. Restart your Spring Boot application"
echo "3. Load your 12 canonical documents using the embedding script"
echo ""

