#!/usr/bin/env python3
"""
Load 12 canonical documents for transaction_keyspace.dda_transactions into YugabyteDB.
Generates embeddings using Phi-4 API and inserts documents with proper doc_sub_type values.
"""

import requests
import psycopg2
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional

# Configuration
PHI4_EMBED_URL = os.getenv("PHI4_EMBED_URL", "http://localhost:8083/api/embed")
PG_CONN = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "5433")),
    "dbname": os.getenv("DB_NAME", "rag_llm_optimized"),
    "user": os.getenv("DB_USER", "yugabyte"),
    "password": os.getenv("DB_PASSWORD", "yugabyte")
}

# Timeout for embedding generation
EMBED_TIMEOUT = 120


def get_embedding(text: str) -> List[float]:
    """
    Generate embedding for text using Phi-4 API.
    
    Args:
        text: Text to embed
        
    Returns:
        List of float values representing the embedding vector
    """
    try:
        response = requests.post(
            PHI4_EMBED_URL,
            json={"text": text},
            timeout=EMBED_TIMEOUT
        )
        response.raise_for_status()
        data = response.json()
        
        # Expect: {"embedding": [0.0123, ...]} or {"status": "success", "embedding": [...]}
        if "embedding" in data:
            embedding = data["embedding"]
        elif "status" in data and data.get("status") == "success" and "embedding" in data:
            embedding = data["embedding"]
        else:
            raise ValueError(f"Unexpected response format: {data}")
        
        if not isinstance(embedding, list) or len(embedding) != 384:
            raise ValueError(f"Expected 384-dimensional embedding, got {len(embedding) if isinstance(embedding, list) else 'non-list'}")
        
        return embedding
        
    except requests.exceptions.RequestException as e:
        print(f"❌ Error calling Phi-4 embedding API: {e}")
        raise
    except (KeyError, ValueError) as e:
        print(f"❌ Error parsing embedding response: {e}")
        raise


def format_embedding_for_pg(embedding: List[float]) -> str:
    """
    Format embedding list as PostgreSQL vector string.
    
    Args:
        embedding: List of float values
        
    Returns:
        PostgreSQL vector string format: "[0.1,0.2,...]"
    """
    return "[" + ",".join(map(str, embedding)) + "]"


def insert_document(doc: Dict, cur) -> None:
    """
    Insert a document into rag_documents table.
    
    Args:
        doc: Document dictionary with all fields
        cur: Database cursor
    """
    try:
        # Generate embedding for content
        print(f"  📝 Generating embedding for {doc.get('doc_sub_type')}...")
        embedding = get_embedding(doc["content"])
        embedding_pg = format_embedding_for_pg(embedding)
        
        # Prepare metadata as JSON string
        metadata_json = json.dumps(doc.get("metadata", {}))
        
        # SQL INSERT with all fields
        sql = """
        INSERT INTO rag_documents
        (cluster_name, source_type, doc_sub_type, entity_type, component, source_name, 
         keyspace, table_name, domain, sub_domain, event_date, time_window, 
         content, metadata, embedding)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s::vector)
        """
        
        # Parse event_date if present
        event_date = None
        if "event_date" in doc and doc["event_date"]:
            from datetime import datetime
            event_date = datetime.strptime(doc["event_date"], "%Y-%m-%d").date()
        
        params = (
            doc.get("cluster_name"),
            doc.get("source_type"),
            doc.get("doc_sub_type"),
            doc.get("entity_type"),
            doc.get("component"),
            doc.get("source_name"),
            doc.get("keyspace"),
            doc.get("table_name"),
            doc.get("domain"),
            doc.get("sub_domain"),
            event_date,
            doc.get("time_window"),
            doc.get("content"),
            metadata_json,
            embedding_pg
        )
        
        cur.execute(sql, params)
        print(f"  ✅ Inserted {doc.get('doc_sub_type')}")
        
    except Exception as e:
        print(f"  ❌ Error inserting {doc.get('doc_sub_type')}: {e}")
        raise


def load_all_documents(data_dir: Path) -> None:
    """
    Load all 12 canonical documents from JSON files.
    
    Args:
        data_dir: Directory containing JSON document files
    """
    # Expected document files in order
    doc_files = [
        "01_business_metadata.json",
        "02_schema_metadata.json",
        "03_storage_configuration.json",
        "04_table_statistics.json",
        "05_data_lifecycle.json",
        "06_lineage_kafka.json",
        "07_lineage_spark.json",
        "08_lineage_dataapi.json",
        "09_logs_daily.json",
        "10_logs_weekly.json",
        "11_metrics_daily.json",
        "12_metrics_weekly.json"
    ]
    
    print(f"📂 Loading documents from {data_dir}")
    print(f"🔗 Phi-4 Embedding API: {PHI4_EMBED_URL}")
    print(f"🗄️  Database: {PG_CONN['host']}:{PG_CONN['port']}/{PG_CONN['dbname']}\n")
    
    # Connect to database
    try:
        conn = psycopg2.connect(**PG_CONN)
        cur = conn.cursor()
        print("✅ Connected to YugabyteDB\n")
    except Exception as e:
        print(f"❌ Error connecting to database: {e}")
        sys.exit(1)
    
    loaded_count = 0
    failed_count = 0
    
    try:
        for doc_file in doc_files:
            file_path = data_dir / doc_file
            if not file_path.exists():
                print(f"⚠️  File not found: {file_path}")
                failed_count += 1
                continue
            
            try:
                with open(file_path, 'r') as f:
                    doc = json.load(f)
                
                print(f"📄 Loading {doc_file} ({doc.get('doc_sub_type')})...")
                insert_document(doc, cur)
                conn.commit()
                loaded_count += 1
                print()
                
            except Exception as e:
                print(f"❌ Error loading {doc_file}: {e}\n")
                conn.rollback()
                failed_count += 1
                continue
        
        print(f"\n{'='*60}")
        print(f"✅ Successfully loaded: {loaded_count} documents")
        if failed_count > 0:
            print(f"❌ Failed: {failed_count} documents")
        print(f"{'='*60}")
        
    finally:
        cur.close()
        conn.close()
        print("\n🔌 Database connection closed")


def main():
    """Main entry point."""
    # Get data directory (default: mvp/data relative to script location)
    script_dir = Path(__file__).parent
    data_dir = script_dir.parent / "data"
    
    if not data_dir.exists():
        print(f"❌ Data directory not found: {data_dir}")
        sys.exit(1)
    
    # Test Phi-4 API connection
    print("🔍 Testing Phi-4 embedding API...")
    try:
        response = requests.get(PHI4_EMBED_URL.replace("/api/embed", "/health"), timeout=5)
        if response.status_code == 200:
            print("✅ Phi-4 API is reachable\n")
        else:
            print(f"⚠️  Phi-4 API returned status {response.status_code}\n")
    except Exception as e:
        print(f"⚠️  Could not reach Phi-4 API: {e}")
        print("   Continuing anyway...\n")
    
    # Load all documents
    load_all_documents(data_dir)


if __name__ == "__main__":
    main()

