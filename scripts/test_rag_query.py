#!/usr/bin/env python3
"""
Robust RAG Query Test - Full flow with progress, error handling, and detailed logging
Retrieves documents from Yugabyte and generates answer using Phi-4 LLM
"""

import requests
import psycopg2
import json
import os
import sys
import time
import threading
from typing import List, Dict, Optional

# Configuration with defaults
EMBED_API_URL = os.environ.get('EMBED_API_URL', 'http://localhost:8083/api/embed')
GENERATE_API_URL = os.environ.get('GENERATE_API_URL', 'http://localhost:8083/api/rag')
DB_CONFIG = {
    'dbname': os.environ.get('DB_NAME', 'postgres'),
    'user': os.environ.get('DB_USER', 'yugabyte'),
    'password': os.environ.get('DB_PASSWORD', 'yugabyte'),
    'host': os.environ.get('DB_HOST', 'localhost'),
    'port': int(os.environ.get('DB_PORT', '5433'))
}

# Timeout configuration
EMBED_TIMEOUT = 120  # 2 minutes for embedding
RAG_TIMEOUT = 600    # 10 minutes for RAG generation (CPU is slow)
MAX_TOKENS = 100     # Reduced for faster CPU inference

class ProgressIndicator:
    """Thread-safe progress indicator"""
    def __init__(self, message="Processing"):
        self.message = message
        self.stop_event = threading.Event()
        self.thread = None
        
    def start(self):
        """Start showing progress"""
        self.stop_event.clear()
        self.thread = threading.Thread(target=self._show_progress, daemon=True)
        self.thread.start()
        
    def stop(self):
        """Stop showing progress"""
        self.stop_event.set()
        if self.thread:
            self.thread.join(timeout=1)
        print()  # New line after progress
        
    def _show_progress(self):
        """Show animated progress dots"""
        dots = 0
        while not self.stop_event.is_set():
            animation = ['...', '..', '.'][dots % 3]
            print(f"\r  {self.message}{animation}", end='', flush=True)
            time.sleep(0.5)
            dots += 1

def check_health() -> Dict[str, bool]:
    """Check health of all services"""
    health = {
        'phi4': False,
        'yugabyte': False
    }
    
    # Check Phi-4
    try:
        health_url = EMBED_API_URL.replace('/api/embed', '/health')
        response = requests.get(health_url, timeout=5)
        health['phi4'] = response.status_code == 200
    except:
        pass
    
    # Check Yugabyte
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        conn.close()
        health['yugabyte'] = True
    except:
        pass
    
    return health

def embed_query(query: str) -> tuple[List[float], float]:
    """Generate embedding for query with progress and error handling
    Returns: (embedding, latency_seconds)
    """
    print(f"  Generating embedding for query...")
    progress = ProgressIndicator("Generating embedding")
    progress.start()
    
    start_time = time.time()
    try:
        response = requests.post(
            EMBED_API_URL,
            json={"text": query},
            timeout=EMBED_TIMEOUT
        )
        elapsed = time.time() - start_time
        progress.stop()
        
        response.raise_for_status()
        result = response.json()
        embedding = result.get('embedding', [])
        
        if len(embedding) != 384:
            raise ValueError(f"Expected 384 dimensions, got {len(embedding)}")
        
        print(f"  ✓ Embedding generated ({len(embedding)} dimensions) - {elapsed:.3f}s")
        return embedding, elapsed
        
    except requests.exceptions.Timeout:
        elapsed = time.time() - start_time
        progress.stop()
        raise Exception(f"Embedding API timeout after {elapsed:.1f}s (>{EMBED_TIMEOUT}s). Check if Phi-4 is running.")
    except requests.exceptions.ConnectionError:
        elapsed = time.time() - start_time
        progress.stop()
        raise Exception(f"Cannot connect to embedding API at {EMBED_API_URL}. Check if Phi-4 container is running.")
    except Exception as e:
        elapsed = time.time() - start_time
        progress.stop()
        raise Exception(f"Embedding error after {elapsed:.1f}s: {str(e)}")

def retrieve_context(query: str, top_k: int = 6) -> tuple[List[Dict], Dict[str, float]]:
    """Retrieve relevant documents from Yugabyte with progress
    Returns: (results, timing_dict) where timing_dict contains 'embedding', 'db_query', 'total'
    """
    print(f"  Retrieving documents from Yugabyte...")
    progress = ProgressIndicator("Searching vector database")
    progress.start()
    
    retrieval_start = time.time()
    timing = {}
    
    try:
        # Generate query embedding
        query_embedding, embed_latency = embed_query(query)
        timing['embedding'] = embed_latency
        
        # Connect to database and query
        db_start = time.time()
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        
        # Format embedding as PostgreSQL vector string
        embedding_str = '[' + ','.join(map(str, query_embedding)) + ']'
        
        # Vector similarity search
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
                'metadata': row[4] if row[4] else {},
                'event_date': str(row[5]) if row[5] else None,
                'similarity': float(row[6]) if row[6] else None
            })
        
        cur.close()
        conn.close()
        db_latency = time.time() - db_start
        timing['db_query'] = db_latency
        
        total_retrieval = time.time() - retrieval_start
        timing['total'] = total_retrieval
        
        progress.stop()
        
        print(f"  ✓ Found {len(results)} relevant documents")
        print(f"  ⏱  Timing: Embedding={timing['embedding']:.3f}s, DB Query={timing['db_query']:.3f}s, Total={timing['total']:.3f}s")
        return results, timing
        
    except psycopg2.OperationalError as e:
        elapsed = time.time() - retrieval_start
        progress.stop()
        raise Exception(f"Database connection error after {elapsed:.1f}s: {str(e)}. Check if Yugabyte is running and DB_NAME=postgres")
    except Exception as e:
        elapsed = time.time() - retrieval_start
        progress.stop()
        raise Exception(f"Retrieval error after {elapsed:.1f}s: {str(e)}")

def generate_answer(query: str, contexts: List[Dict]) -> tuple[str, float]:
    """Generate answer using RAG with progress, timeout, and error handling
    Returns: (answer, latency_seconds)
    """
    # Combine contexts
    context_text = "\n\n".join([
        f"[{ctx['source_type']} - {ctx['component']}]\n{ctx['content']}" 
        for ctx in contexts
    ])
    
    print(f"  Context prepared: {len(context_text)} characters from {len(contexts)} documents")
    print(f"  Calling Phi-4 RAG API at: {GENERATE_API_URL}")
    print(f"  ⏱  This may take 2-5 minutes on CPU (please wait)...")
    print(f"  📊 Request: max_tokens={MAX_TOKENS}, temperature=0.3")
    
    progress = ProgressIndicator("Generating answer")
    progress.start()
    
    start_time = time.time()
    
    try:
        response = requests.post(
            GENERATE_API_URL,
            json={
                "query": query,
                "context": context_text,
                "max_tokens": MAX_TOKENS,
                "temperature": 0.3
            },
            timeout=RAG_TIMEOUT
        )
        
        elapsed = time.time() - start_time
        progress.stop()
        
        print(f"  ✓ Response received after {elapsed:.3f}s ({elapsed/60:.2f} minutes)")
        print(f"  Status Code: {response.status_code}")
        
        response.raise_for_status()
        result = response.json()
        
        answer = result.get('text', '')
        status = result.get('status', '')
        
        if not answer or answer.strip() == "":
            print(f"  ⚠ Warning: Empty answer in response")
            print(f"  Response JSON: {json.dumps(result, indent=2)}")
            return "Error: Empty answer received from API. Check Phi-4 container logs.", elapsed
        
        if status != 'success':
            print(f"  ⚠ Warning: Status is '{status}', not 'success'")
        
        return answer.strip(), elapsed
        
    except requests.exceptions.Timeout:
        elapsed = time.time() - start_time
        progress.stop()
        print(f"\n  ✗ Timeout: Request took {elapsed:.1f} seconds (>{RAG_TIMEOUT}s)")
        print(f"  💡 Tips:")
        print(f"     - CPU inference is very slow (2-5 minutes is normal)")
        print(f"     - Try reducing max_tokens (currently {MAX_TOKENS})")
        print(f"     - Check container logs: docker logs phi4-rag-api-q3")
        return f"Error: Request timed out after {elapsed:.1f} seconds. CPU inference is very slow.", elapsed
        
    except requests.exceptions.ConnectionError as e:
        elapsed = time.time() - start_time
        progress.stop()
        print(f"\n  ✗ Connection Error: {e}")
        print(f"  💡 Troubleshooting:")
        print(f"     1. Check if container is running: docker ps | grep phi4")
        print(f"     2. Check container logs: docker logs phi4-rag-api-q3")
        print(f"     3. Test health: curl {GENERATE_API_URL.replace('/api/rag', '/health')}")
        return f"Error: Cannot connect to Phi-4 API. Check if container is running at {GENERATE_API_URL}", elapsed
        
    except requests.exceptions.HTTPError as e:
        elapsed = time.time() - start_time
        progress.stop()
        print(f"\n  ✗ HTTP Error: {e.response.status_code}")
        if hasattr(e, 'response') and e.response.text:
            print(f"  Response: {e.response.text[:300]}")
        return f"Error: HTTP {e.response.status_code} - {e.response.text[:200] if hasattr(e, 'response') else str(e)}", elapsed
        
    except Exception as e:
        elapsed = time.time() - start_time
        progress.stop()
        print(f"\n  ✗ Unexpected Error: {type(e).__name__}: {e}")
        import traceback
        traceback.print_exc()
        return f"Error: {str(e)}", elapsed

def main():
    """Main function with full error handling and progress tracking"""
    if len(sys.argv) < 2:
        print("Usage: python test_rag_query.py '<your question>'")
        print("\nExample questions:")
        print("  - What is the schema of dda_transactions?")
        print("  - Why was dda_transactions delayed yesterday?")
        print("  - What caused Kafka lag?")
        print("  - Which component was the bottleneck?")
        sys.exit(1)
    
    query = sys.argv[1]
    
    print("=" * 80)
    print("RAG Query Test - Full Flow")
    print("=" * 80)
    print(f"\nQuestion: {query}")
    print()
    
    # Pre-flight checks
    print("Pre-flight Checks:")
    print("-" * 80)
    health = check_health()
    
    if not health['phi4']:
        print("  ✗ Phi-4 API: NOT AVAILABLE")
        print(f"     Check: curl {EMBED_API_URL.replace('/api/embed', '/health')}")
        print(f"     Or: docker ps | grep phi4")
        sys.exit(1)
    else:
        print("  ✓ Phi-4 API: Available")
    
    if not health['yugabyte']:
        print("  ✗ Yugabyte: NOT AVAILABLE")
        print(f"     Check connection to {DB_CONFIG['host']}:{DB_CONFIG['port']}")
        print(f"     Database: {DB_CONFIG['dbname']}")
        sys.exit(1)
    else:
        print("  ✓ Yugabyte: Available")
    
    print()
    
    # Track total time
    total_start_time = time.time()
    
    # Step 1: Retrieve context
    print("=" * 80)
    print("Step 1: Retrieving Relevant Documents")
    print("=" * 80)
    retrieval_timing = {}
    try:
        contexts, retrieval_timing = retrieve_context(query, top_k=6)
        print()
        
        if not contexts:
            print("  ⚠ No documents found!")
            print("  Check if data was loaded: SELECT COUNT(*) FROM rag_documents;")
            sys.exit(1)
        
        # Show retrieved documents summary
        print("Retrieved Documents Summary:")
        for i, ctx in enumerate(contexts, 1):
            similarity = ctx.get('similarity', 0)
            print(f"  [{i}] {ctx['source_type']} - {ctx['component']} "
                  f"(similarity: {similarity:.3f})")
        print()
        
    except Exception as e:
        print(f"\n✗ Step 1 Failed: {e}")
        sys.exit(1)
    
    # Step 2: Generate answer
    print("=" * 80)
    print("Step 2: Generating Answer with Phi-4 LLM")
    print("=" * 80)
    generation_latency = 0.0
    try:
        answer, generation_latency = generate_answer(query, contexts)
        print()
        
    except Exception as e:
        print(f"\n✗ Step 2 Failed: {e}")
        sys.exit(1)
    
    # Calculate total time
    total_elapsed = time.time() - total_start_time
    
    # Display results
    print("=" * 80)
    print("FINAL ANSWER")
    print("=" * 80)
    print(answer)
    print()
    
    # Show sources
    print("=" * 80)
    print("SOURCE DOCUMENTS (Used for Answer)")
    print("=" * 80)
    for i, ctx in enumerate(contexts, 1):
        print(f"\n[{i}] {ctx['source_type']} - {ctx['component']}")
        if ctx.get('source_name'):
            print(f"    Source: {ctx['source_name']}")
        if ctx.get('event_date'):
            print(f"    Date: {ctx['event_date']}")
        if ctx.get('similarity'):
            print(f"    Relevance: {ctx['similarity']:.1%}")
        print(f"    Content: {ctx['content'][:300]}...")
        if ctx.get('metadata'):
            print(f"    Metadata: {json.dumps(ctx['metadata'], indent=6)}")
    
    # Display latency summary
    print("\n" + "=" * 80)
    print("LATENCY BREAKDOWN")
    print("=" * 80)
    print(f"Step 1 - Document Retrieval:")
    print(f"  • Embedding Generation:    {retrieval_timing.get('embedding', 0):.3f}s")
    print(f"  • Database Vector Search:  {retrieval_timing.get('db_query', 0):.3f}s")
    print(f"  • Total Retrieval Time:    {retrieval_timing.get('total', 0):.3f}s")
    print(f"Step 2 - Answer Generation:")
    print(f"  • LLM Inference:          {generation_latency:.3f}s ({generation_latency/60:.2f} min)")
    print(f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"TOTAL END-TO-END LATENCY:   {total_elapsed:.3f}s ({total_elapsed/60:.2f} min)")
    print("=" * 80)
    print("✓ Query completed successfully!")
    print("=" * 80)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠ Interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n✗ Fatal Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
