-- Create new database for RAG LLM Optimized
-- This database will use HNSW index with 384-dim embeddings

-- Connect to default postgres database first, then run:
CREATE DATABASE rag_llm_optimized;

-- After creating the database, connect to rag_llm_optimized and run:
-- 02_create_schema_hnsw_384.sql

-- To connect to the new database:
-- \c rag_llm_optimized

