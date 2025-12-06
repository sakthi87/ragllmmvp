#!/usr/bin/env python3
"""
Generate embeddings using Phi-4 Flask API and load into Yugabyte
Works in restricted environments (offline mode)
"""

import requests
import psycopg2
import json
import os
import sys
from typing import List, Dict

# Configuration
EMBED_API_URL = os.environ.get('EMBED_API_URL', 'http://localhost:8082/api/embed')
DB_CONFIG = {
    'dbname': os.environ.get('DB_NAME', 'yugabyte'),
    'user': os.environ.get('DB_USER', 'yugabyte'),
    'password': os.environ.get('DB_PASSWORD', 'yugabyte'),
    'host': os.environ.get('DB_HOST', 'localhost'),
    'port': int(os.environ.get('DB_PORT', '5433'))
}

def embed_text(text: str) -> List[float]:
    """Generate embedding for text using Phi-4 API"""
    try:
        response = requests.post(
            EMBED_API_URL,
            json={"text": text},
            timeout=30
        )
        response.raise_for_status()
        result = response.json()
        return result.get('embedding', [])
    except requests.exceptions.RequestException as e:
        print(f"Error calling embedding API: {e}")
        raise

def load_document(doc: Dict, cur) -> None:
    """Load a single document into Yugabyte with embedding"""
    try:
        # Generate embedding
        print(f"Generating embedding for: {doc.get('source_name', 'unknown')}...")
        embedding = embed_text(doc['content'])
        
        if len(embedding) != 384:
            raise ValueError(f"Expected 384-dimensional embedding, got {len(embedding)}")
        
        # Insert into database
        # Format embedding as PostgreSQL vector string: '[0.1,0.2,0.3,...]'
        embedding_str = '[' + ','.join(map(str, embedding)) + ']'
        
        cur.execute("""
            INSERT INTO rag_documents
            (source_type, component, source_name, keyspace, table_name,
             domain, sub_domain, event_date, content, metadata, embedding)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::vector)
        """, (
            doc.get('source_type'),
            doc.get('component'),
            doc.get('source_name'),
            doc.get('keyspace'),
            doc.get('table_name'),
            doc.get('domain'),
            doc.get('sub_domain'),
            doc.get('event_date'),
            doc.get('content'),
            json.dumps(doc.get('metadata', {})),
            embedding_str
        ))
        
        print(f"  ✓ Inserted: {doc.get('source_name')}")
        
    except Exception as e:
        print(f"  ✗ Error loading {doc.get('source_name')}: {e}")
        raise

def main():
    """Main function to load all documents"""
    print("=" * 80)
    print("RAG MVP - Embedding Generation and Bulk Load")
    print("=" * 80)
    print()
    
    # Load metadata
    print("Loading metadata document...")
    with open('data/metadata.json', 'r') as f:
        metadata_doc = json.load(f)
    
    # Load lineage
    print("Loading lineage document...")
    with open('data/lineage.json', 'r') as f:
        lineage_doc = json.load(f)
    
    # Load logs and metrics
    print("Loading logs and metrics (7 days)...")
    with open('data/logs_metrics_7days.json', 'r') as f:
        logs_metrics = json.load(f)
    
    # Combine all documents
    all_docs = [metadata_doc, lineage_doc] + logs_metrics
    print(f"\nTotal documents to load: {len(all_docs)}")
    print()
    
    # Connect to database
    print(f"Connecting to Yugabyte at {DB_CONFIG['host']}:{DB_CONFIG['port']}...")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        print("✓ Connected to database")
    except Exception as e:
        print(f"✗ Database connection failed: {e}")
        sys.exit(1)
    
    # Load documents
    print("\nGenerating embeddings and loading documents...")
    print("-" * 80)
    
    success_count = 0
    error_count = 0
    
    for i, doc in enumerate(all_docs, 1):
        try:
            print(f"[{i}/{len(all_docs)}] ", end="")
            load_document(doc, cur)
            conn.commit()
            success_count += 1
        except Exception as e:
            print(f"  Error: {e}")
            conn.rollback()
            error_count += 1
    
    # Summary
    print()
    print("=" * 80)
    print("Load Summary")
    print("=" * 80)
    print(f"Successfully loaded: {success_count}")
    print(f"Errors: {error_count}")
    print(f"Total: {len(all_docs)}")
    print()
    
    # Verify count
    cur.execute("SELECT COUNT(*) FROM rag_documents")
    count = cur.fetchone()[0]
    print(f"Total documents in database: {count}")
    
    cur.close()
    conn.close()
    print("\n✓ Done!")

if __name__ == "__main__":
    # Change to script directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    os.chdir('..')  # Go to mvp directory
    
    main()

