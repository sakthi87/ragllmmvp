# LLM Performance Optimization - Immediate Fixes

## 🎯 Problem Statement

**Current Performance**: 4.4 minutes (264,994ms) for 100 tokens  
**Target Performance**: 30-60 seconds  
**Gap**: 4-8x slower than expected

**Root Causes Identified**:
1. ✅ Already using Q3 (optimal quantization)
2. ⚠️ **Context window too large**: `n_ctx=2048` (should be 512 for simple queries)
3. ⚠️ **Threading not optimized**: `n_threads=None` (should be explicit)

---

## 🔧 Immediate Fixes (P0)

### Fix 1: Reduce Context Window (CRITICAL)

**Current Code** (`api_server.py` line 68):
```python
llm_model = Llama(
    model_path=model_path,
    n_ctx=2048,  # ❌ Too large for simple queries
    n_threads=None,
    n_gpu_layers=0,
    verbose=False
)
```

**Fixed Code**:
```python
llm_model = Llama(
    model_path=model_path,
    n_ctx=512,  # ✅ Reduced from 2048 (4x smaller = much faster)
    n_threads=None,
    n_gpu_layers=0,
    verbose=False
)
```

**Expected Impact**: 40-60% faster inference

**Why**: 
- Your prompt is only ~1408 characters (~350-400 tokens)
- 2048 token context window is overkill
- Smaller context = exponentially faster inference

---

### Fix 2: Optimize Threading (CRITICAL)

**Current Code** (`api_server.py` line 69):
```python
n_threads=None,  # ❌ Auto-detect may not use all cores
```

**Fixed Code**:
```python
import os

# Get CPU count, use all cores (or leave 1 for system)
cpu_count = os.cpu_count() or 4
n_threads = max(1, cpu_count - 1)  # Leave 1 core for system

llm_model = Llama(
    model_path=model_path,
    n_ctx=512,
    n_threads=n_threads,  # ✅ Explicitly set
    n_gpu_layers=0,
    verbose=False
)
```

**Expected Impact**: 30-50% faster inference

**Why**:
- Auto-detect may not use all available CPU cores
- Explicit threading ensures maximum CPU utilization
- For MacBook Pro: typically 8-10 cores → use 7-9 threads

---

### Fix 3: Combined Optimization (RECOMMENDED)

**Complete Fixed Code** (`api_server.py` lines 60-73):

```python
# Load Phi-4 GGUF model
logger.info("Loading Phi-4 GGUF model...")
model_path = os.environ.get('MODEL_PATH', '/app/models/phi-4-Q3_0.gguf')

if not os.path.exists(model_path):
    raise FileNotFoundError(f"Model file not found: {model_path}")

# Optimize threading: use all CPU cores
import os
cpu_count = os.cpu_count() or 4
n_threads = max(1, cpu_count - 1)  # Leave 1 core for system
logger.info(f"Using {n_threads} CPU threads for inference")

llm_model = Llama(
    model_path=model_path,
    n_ctx=512,  # Reduced from 2048 for faster inference
    n_threads=n_threads,  # Explicitly set for optimal performance
    n_gpu_layers=0,
    verbose=False
)
logger.info("✓ Phi-4 GGUF model loaded")
```

**Expected Combined Impact**: 50-70% faster inference
- **Before**: 264 seconds (4.4 minutes)
- **After**: 60-90 seconds (1-1.5 minutes)

---

## 📊 Performance Comparison

| Configuration | Context Window | Threading | Expected Time | Improvement |
|---------------|----------------|-----------|----------------|-------------|
| **Current** | 2048 | Auto | 264s (4.4 min) | Baseline |
| **Fix 1 Only** | 512 | Auto | ~120s (2 min) | 2.2x faster |
| **Fix 2 Only** | 2048 | Explicit | ~180s (3 min) | 1.5x faster |
| **Both Fixes** | 512 | Explicit | **60-90s (1-1.5 min)** | **3-4x faster** |

---

## 🚀 Implementation Steps

### Step 1: Update `api_server.py`

1. Open `api_server.py` in the Docker container or source code
2. Find the `load_models()` function (around line 60)
3. Apply the combined fix above
4. Save the file

### Step 2: Rebuild Docker Container (if using Docker)

```bash
# If you have the source code
docker build -t sakthipsgit/phi4-rag-combined-q3:latest .

# Or if you need to modify the running container
docker exec -it <container_id> /bin/bash
# Edit api_server.py inside container
# Restart the container
```

### Step 3: Restart the Service

```bash
# Restart the Phi-4 container
docker restart <container_id>

# Or if running directly
python api_server.py
```

### Step 4: Test Performance

Run the same query:
```
"What is the schema of dda_transactions?"
```

**Expected Results**:
- Before: 264 seconds
- After: 60-90 seconds
- **Improvement**: 3-4x faster

---

## 🔍 Additional Optimizations (Optional)

### Option A: Dynamic Context Window

For different query types, use different context sizes:

```python
def get_optimal_context_size(query_type):
    """Return optimal context size based on query complexity"""
    if query_type in ['schema_metadata', 'table_statistics']:
        return 512  # Simple queries
    elif query_type in ['logs_daily', 'metrics_daily']:
        return 1024  # Medium complexity
    else:
        return 2048  # Complex analysis
```

### Option B: Check Docker CPU Limits

```bash
# Check current CPU usage
docker stats <container_id>

# If CPU is limited, increase allocation
docker update --cpus="8" <container_id>  # Use 8 CPUs
```

### Option C: Monitor Performance

Add logging to track generation speed:

```python
import time

start_time = time.time()
response = llm_model(prompt, max_tokens=max_tokens, ...)
duration = time.time() - start_time
tokens_per_second = max_tokens / duration

logger.info(f"Generation: {duration:.2f}s, {tokens_per_second:.2f} tokens/s")
```

---

## ✅ Validation Checklist

After implementing fixes, verify:

- [ ] Context window reduced to 512
- [ ] Threading explicitly set to CPU count
- [ ] Docker container restarted
- [ ] Test query completes in 60-90 seconds
- [ ] Answer quality remains excellent
- [ ] No errors in logs

---

## 📝 Summary

**Key Changes**:
1. ✅ `n_ctx=2048` → `n_ctx=512` (4x reduction)
2. ✅ `n_threads=None` → `n_threads=explicit` (use all cores)

**Expected Results**:
- **3-4x faster inference** (264s → 60-90s)
- **Better CPU utilization**
- **Same answer quality**

**Next Steps**:
- Implement fixes
- Test performance
- Monitor results
- Consider additional optimizations if needed

---

**Priority**: 🔥 **P0 - CRITICAL**  
**Effort**: ⚡ **Low (5-10 minutes)**  
**Impact**: 🚀 **High (3-4x improvement)**

