#!/usr/bin/env python3
"""
Direct API test - Test Phi-4 RAG API directly without retrieval
Useful for debugging API connection issues
"""

import requests
import sys
import os

GENERATE_API_URL = os.environ.get('GENERATE_API_URL', 'http://localhost:8083/api/rag')

def test_rag_api():
    """Test RAG API directly"""
    print("=" * 80)
    print("Direct Phi-4 RAG API Test")
    print("=" * 80)
    print(f"\nAPI URL: {GENERATE_API_URL}")
    print()
    
    # Simple test
    test_query = "What is AI?"
    test_context = "Artificial intelligence is the simulation of human intelligence by machines."
    
    print(f"Query: {test_query}")
    print(f"Context: {test_context}")
    print(f"\nSending request (this may take 1-3 minutes on CPU)...")
    
    try:
        import time
        start = time.time()
        
        response = requests.post(
            GENERATE_API_URL,
            json={
                "query": test_query,
                "context": test_context,
                "max_tokens": 20,  # Very small for quick test
                "temperature": 0.3
            },
            timeout=300  # 5 minutes
        )
        
        elapsed = time.time() - start
        
        print(f"\n✓ Response received after {elapsed:.1f} seconds")
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"Response: {result}")
            text = result.get('text', '')
            if text:
                print(f"\n✓ Success! Generated text: {text}")
            else:
                print(f"\n⚠ Warning: Empty text in response")
        else:
            print(f"\n✗ Error: Status {response.status_code}")
            print(f"Response: {response.text}")
            
    except requests.exceptions.Timeout:
        print(f"\n✗ Timeout: Request took longer than 5 minutes")
        print("This is normal for CPU-only inference. Try increasing timeout.")
    except requests.exceptions.ConnectionError as e:
        print(f"\n✗ Connection Error: {e}")
        print(f"\nTroubleshooting:")
        print(f"  1. Check if container is running: docker ps | grep phi4")
        print(f"  2. Check container logs: docker logs phi4-rag-api-q3")
        print(f"  3. Test health: curl http://localhost:8083/health")
    except Exception as e:
        print(f"\n✗ Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_rag_api()

