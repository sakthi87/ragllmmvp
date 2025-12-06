# Troubleshooting: RAG Query Not Generating Answers

## Issue: Script retrieves documents but doesn't generate answer

### Quick Tests

#### 1. Test Phi-4 API Directly
```bash
cd mvp/scripts
export GENERATE_API_URL=http://localhost:8083/api/rag
python3 test_api_direct.py
```
This tests the API without retrieval to isolate the issue.

#### 2. Test Retrieval Only
```bash
cd mvp/scripts
export EMBED_API_URL=http://localhost:8083/api/embed
export DB_NAME=postgres
python3 test_rag_simple.py "why was dda_transactions delayed yesterday?"
```
This tests retrieval without LLM generation.

#### 3. Check Phi-4 Container
```bash
# Check if running
docker ps | grep phi4-rag-api-q3

# Check logs
docker logs phi4-rag-api-q3 | tail -30

# Test health
curl http://localhost:8083/health
```

### Common Issues

#### Issue 1: API Timeout
**Symptom**: Script hangs, no response
**Solution**: 
- CPU inference is very slow (2-5 minutes per request)
- Wait longer (up to 10 minutes)
- Reduce max_tokens to 20-30 for faster response

#### Issue 2: Connection Closed
**Symptom**: "Connection prematurely closed"
**Solution**:
- Check container logs: `docker logs phi4-rag-api-q3`
- Restart container: `docker restart phi4-rag-api-q3`
- Check if container has enough memory

#### Issue 3: Wrong Port/Database
**Symptom**: Connection refused
**Solution**:
```bash
export EMBED_API_URL=http://localhost:8083/api/embed
export GENERATE_API_URL=http://localhost:8083/api/rag
export DB_NAME=postgres  # Not yugabyte!
```

#### Issue 4: Empty Response
**Symptom**: Response received but text is empty
**Solution**:
- Check API response: `curl -X POST http://localhost:8083/api/rag -H "Content-Type: application/json" -d '{"query":"test","context":"test","max_tokens":10}'`
- Check container logs for errors
- Try smaller max_tokens (10-20)

### Recommended Approach

1. **First**: Test API directly
   ```bash
   python3 mvp/scripts/test_api_direct.py
   ```

2. **Second**: Test retrieval only
   ```bash
   python3 mvp/scripts/test_rag_simple.py "your question"
   ```

3. **Third**: Test full RAG
   ```bash
   python3 mvp/scripts/test_rag_query.py "your question"
   ```

### Performance Tips

- Use `max_tokens: 20-50` for faster responses
- CPU inference: 2-5 minutes per request is normal
- For production: Use GPU or smaller model (Q3 is faster than Q4/Q5)

