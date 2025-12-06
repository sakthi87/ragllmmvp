# System Resource Assessment for Phi-4 Q3 Local Testing

## Current System Resources

### Hardware Specifications
- **CPU**: 8 cores (8 physical, 8 logical)
- **Total RAM**: 8 GB
- **Available Disk**: 52 GB (out of 460 GB total, 17% used)
- **Docker Memory Limit**: 3.828 GiB

### Current Resource Usage

#### System Memory
- **Used**: 7.5 GB / 8 GB (94% used)
- **Wired**: 1.9 GB
- **Compressed**: 3.0 GB
- **Unused**: 97 MB
- **CPU**: 9.88% user, 17.19% sys, 72.92% idle

#### Running Services
- **YugabyteDB**: Running (container: `yugabyte`)
- **Phi-4 Q3 Container**: Exists (`phi4-rag-api-q3`) - Status: Unknown
- **Active Java/Node Processes**: 7 processes
- **Docker Containers**: 1 active (Yugabyte using ~222 MB)

---

## Phi-4 Q3 Resource Requirements

### Model Size
- **Docker Image**: 8.97 GB (already downloaded)
- **Model File (Q3_K_M)**: ~2-3 GB (included in image)
- **Embedding Model**: ~500 MB (all-MiniLM-L6-v2)

### Runtime Memory Requirements
- **Model Loading**: ~2-3 GB RAM
- **Embedding Model**: ~500 MB RAM
- **Inference Overhead**: ~1-2 GB RAM
- **Python/Flask Runtime**: ~200-300 MB
- **Total Estimated**: **4-6 GB RAM**

### CPU Requirements
- **Inference**: CPU-only (no GPU)
- **Expected Latency**: 2-5 minutes per query (CPU inference is slow)
- **CPU Usage**: Will spike to 50-100% during inference

---

## Assessment: Can You Run Phi-4 Q3 Locally?

### ✅ **YES, but with limitations**

#### Available Resources
- ✅ **CPU**: 8 cores is sufficient (CPU inference will be slow but workable)
- ✅ **Disk Space**: 52 GB available (plenty for model and data)
- ✅ **Docker**: Already set up and working
- ✅ **Model Image**: Already downloaded (8.97 GB)

#### ⚠️ **Memory Constraints**
- ⚠️ **Tight Memory**: 8 GB total with 7.5 GB already used
- ⚠️ **Docker Limit**: Only 3.8 GB allocated to Docker
- ⚠️ **Yugabyte**: Already using ~222 MB
- ⚠️ **Available for Phi-4**: ~3.5 GB in Docker (may be insufficient)

### Risk Assessment

| Component | Status | Risk |
|-----------|--------|------|
| CPU | ✅ Sufficient | Low |
| Disk Space | ✅ Sufficient | Low |
| Docker Setup | ✅ Working | Low |
| Memory | ⚠️ Tight | **Medium-High** |
| Model Size | ✅ Downloaded | Low |

---

## Recommendations

### Option 1: Try Running (Recommended First Step)
**Action**: Start Phi-4 Q3 container and monitor memory usage

```bash
# Check if container is running
docker ps -a | grep phi4-rag-api-q3

# If stopped, start it
docker start phi4-rag-api-q3

# Monitor memory usage
docker stats phi4-rag-api-q3

# Check logs
docker logs phi4-rag-api-q3
```

**Expected Outcome**:
- ✅ **Best Case**: Container starts, model loads, queries work (slow but functional)
- ⚠️ **Worst Case**: Out of memory error, container crashes

### Option 2: Increase Docker Memory (If Option 1 Fails)
**Action**: Increase Docker Desktop memory allocation

1. Open Docker Desktop
2. Go to Settings → Resources → Advanced
3. Increase Memory from 3.8 GB to **6-7 GB**
4. Apply & Restart
5. Retry Option 1

**Trade-off**: Less memory for macOS, but more for Docker

### Option 3: Stop Unnecessary Services
**Action**: Free up system memory before starting Phi-4

```bash
# Check what's using memory
top -l 1 -o mem | head -20

# Stop unnecessary applications
# - Close browser tabs
# - Close IDEs if not needed
# - Stop other Docker containers
```

### Option 4: Use Smaller Model (If Memory Issues Persist)
**Action**: Consider using a smaller quantization (Q2) or wait for GPU

- Q2 model uses less memory (~1.5-2 GB)
- But quality may be lower
- Or wait until you have GPU access

---

## Testing Plan

### Step 1: Verify Current State
```bash
# Check container status
docker ps -a | grep phi4

# Check if port 8083 is available
lsof -i :8083

# Check Yugabyte is running
docker ps | grep yugabyte
```

### Step 2: Start Phi-4 Q3 Container
```bash
# Start container (if stopped)
docker start phi4-rag-api-q3

# Or run new container
docker run -d \
  --name phi4-rag-api-q3 \
  -p 8083:5000 \
  -e CURL_CA_BUNDLE="" \
  -e REQUESTS_CA_BUNDLE="" \
  -e HF_HUB_DISABLE_EXPERIMENTAL_WARNING=1 \
  --memory="4g" \
  --cpus="4" \
  sakthipsgit/phi4-rag-combined-q3:latest
```

**Note**: Added `--memory="4g"` and `--cpus="4"` limits to prevent system overload

### Step 3: Monitor Resource Usage
```bash
# Watch container stats
watch -n 2 'docker stats phi4-rag-api-q3 --no-stream'

# Check system memory
vm_stat | grep "Pages free"

# Check if container is healthy
docker logs phi4-rag-api-q3 --tail 50
```

### Step 4: Test Endpoints
```bash
# Test health endpoint
curl http://localhost:8083/health

# Test embedding endpoint
curl -X POST http://localhost:8083/api/embed \
  -H "Content-Type: application/json" \
  -d '{"text": "test query"}'

# Test RAG endpoint (will be slow - 2-5 minutes)
curl -X POST http://localhost:8083/api/rag \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the schema of dda_transactions?",
    "context": "Test context",
    "max_tokens": 50,
    "temperature": 0.3
  }'
```

### Step 5: Test Full RAG Flow
```bash
# Run test script
cd mvp/scripts
python3 test_rag_query.py "What is the schema of dda_transactions?"
```

---

## Expected Performance

### With Current Resources (8 GB RAM, CPU-only)

| Operation | Expected Time | Notes |
|-----------|---------------|-------|
| Container Start | 30-60 seconds | Model loading |
| Embedding Generation | 200-500ms | Fast |
| Vector Search | 50-200ms | Fast (Yugabyte) |
| LLM Inference (50 tokens) | 2-5 minutes | **Very slow on CPU** |
| LLM Inference (100 tokens) | 4-8 minutes | **Very slow on CPU** |
| Full RAG Query | 2-5 minutes | Mostly LLM time |

### Memory Usage During Inference
- **Idle**: ~2-3 GB
- **During Inference**: ~4-5 GB (peak)
- **After Inference**: ~2-3 GB

---

## Troubleshooting

### If Container Fails to Start
```bash
# Check logs
docker logs phi4-rag-api-q3

# Common errors:
# - "Out of memory" → Increase Docker memory or reduce model size
# - "Port already in use" → Stop other containers using port 8083
# - "Model file not found" → Check image has model embedded
```

### If Container Crashes During Inference
```bash
# Check memory limits
docker stats phi4-rag-api-q3

# Reduce max_tokens in requests
# Use smaller context size
# Restart container
```

### If System Becomes Unresponsive
```bash
# Stop container immediately
docker stop phi4-rag-api-q3

# Free up memory
docker system prune -f

# Restart with lower memory limit
docker run ... --memory="3g" ...
```

---

## Conclusion

### ✅ **You CAN run Phi-4 Q3 locally, but:**

1. **Memory is tight** - Monitor closely
2. **CPU inference is slow** - Expect 2-5 minute response times
3. **Start with small tests** - Use `max_tokens=50` initially
4. **Have a backup plan** - Be ready to increase Docker memory or use cloud

### Recommended Approach

1. ✅ **Try running** with current setup (Option 1)
2. ⚠️ **Monitor memory** closely during first few queries
3. 🔧 **Adjust if needed** (increase Docker memory or reduce model size)
4. ✅ **Test incrementally** (start with embedding, then small RAG queries)

### Success Criteria

- ✅ Container starts and stays running
- ✅ Health endpoint responds
- ✅ Embedding endpoint works (< 1 second)
- ✅ Small RAG query completes (< 5 minutes)
- ✅ No memory errors in logs

---

## Next Steps

1. **Check current container status**: `docker ps -a | grep phi4`
2. **Start container** (if stopped) or run new one
3. **Monitor resources** during first query
4. **Adjust if needed** based on actual usage
5. **Proceed with full testing** if stable

Good luck! 🚀

