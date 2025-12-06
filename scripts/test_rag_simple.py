#!/usr/bin/env python3
"""
Simplified RAG test - Just test retrieval and show what would be sent to LLM
Useful for debugging when LLM generation is slow
"""

import requests
import psycopg2
import json
import os
import sys

# Configuration
EMBED_API_URL = os.environ.get('EMBED_API_URL', 'http://localhost:8083/api/embed')
DB_CONFIG = {
    'dbname': os.environ.get('DB_NAME', 'postgres'),
    'user': os.environ.get('DB_USER', 'yugabyte'),
    'password': os.environ.get('DB_PASSWORD', 'yugabyte'),
    'host': os.environ.get('DB_HOST', 'localhost'),
    'port': int(os.environ.get('DB_PORT', '5433'))
}

def embed_query(query: str) -> list:
    """Generate embedding for query"""
    print(f"  Generating embedding...")
    response = requests.post(EMBED_API_URL, json={"text": query}, timeout=60)
    response.raise_for_status()
    embedding = response.json().get('embedding', [])
    print(f"  ✓ Embedding generated ({len(embedding)} dimensions)")
    return embedding

def retrieve_context(query: str, top_k: int = 6) -> list:
    """Retrieve relevant documents from Yugabyte"""
    query_embedding = embed_query(query)
    
    # Connect to database
    print(f"  Connecting to database...")
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    
    # Format embedding as PostgreSQL vector string
    embedding_str = '[' + ','.join(map(str, query_embedding)) + ']'
    
    # Vector similarity search
    print(f"  Searching vector database...")
    cur.execute("""
        SELECT source_type, component, source_name, content, metadata, event_date,
               1 - (embedding <=> %s::vector) as similarity
        FROM rag_documents
        WHERE table_name = 'dda_transactions'
        ORDER BY embedding <=> %s::vector
        LIMIT %s
    """, (embedding_str, embedding_str, top_k))
    
    results = []
    for row in cur.fetchall():
        results.append({
            'source_type': row[0],
            'component': row[1],
            'source_name': row[2],
            'content': row[3],
            'metadata': row[4],
            'event_date': str(row[5]) if row[5] else None,
            'similarity': float(row[6]) if row[6] else None
        })
    
    cur.close()
    conn.close()
    
    return results

def main():
    """Test RAG retrieval only"""
    if len(sys.argv) < 2:
        print("Usage: python test_rag_simple.py '<your question>'")
        sys.exit(1)
    
    query = sys.argv[1]
    
    print("=" * 80)
    print("RAG Retrieval Test (No LLM Generation)")
    print("=" * 80)
    print(f"\nQuestion: {query}")
    print()
    
    # Retrieve context
    print("Retrieving relevant documents...")
    contexts = retrieve_context(query)
    print(f"✓ Found {len(contexts)} relevant documents\n")
    
    # Show retrieved documents
    print("=" * 80)
    print("Retrieved Documents (sorted by relevance):")
    print("=" * 80)
    for i, ctx in enumerate(contexts, 1):
        print(f"\n[{i}] {ctx['source_type']} - {ctx['component']}")
        if ctx.get('similarity'):
            print(f"    Similarity: {ctx['similarity']:.3f} ({ctx['similarity']*100:.1f}% match)")
        if ctx.get('event_date'):
            print(f"    Date: {ctx['event_date']}")
        print(f"    Content: {ctx['content']}")
        if ctx.get('metadata'):
            print(f"    Metadata: {json.dumps(ctx['metadata'], indent=6)}")
    
    # Show what would be sent to LLM
    print("\n" + "=" * 80)
    print("Context that would be sent to LLM:")
    print("=" * 80)
    context_text = "\n\n".join([f"[{ctx['source_type']} - {ctx['component']}]\n{ctx['content']}" for ctx in contexts])
    print(context_text)
    print(f"\nTotal context length: {len(context_text)} characters")
    print(f"\nTo generate answer, this context would be sent to Phi-4 API")
    print(f"Note: CPU inference may take 2-5 minutes for this query")

if __name__ == "__main__":
    main()

