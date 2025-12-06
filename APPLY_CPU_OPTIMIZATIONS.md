# How to Apply CPU Optimizations

## ✅ Status

The optimization guides have been pushed to GitHub:
- ✅ `CPU_OPTIMIZATION_GUIDE.md` - Detailed implementation guide
- ✅ `OPTIMIZATION_SUMMARY.md` - Quick reference

## 📝 Next Steps: Apply Changes to Docker Container

Since `api_server.py` is inside the Docker container (`sakthipsgit/phi4-rag-combined-q3:latest`), you have two options:

### Option 1: Update Running Container (Quick Test)

```bash
# 1. Copy optimized api_server.py into container
docker cp /path/to/optimized/api_server.py <container_id>:/app/api_server.py

# 2. Restart container
docker restart <container_id>

# 3. Test performance
# Run your query and check if it's 4-8x faster
```

### Option 2: Rebuild Docker Image (Permanent)

If you have access to the Docker image source:

1. Update `api_server.py` with the optimizations (see `CPU_OPTIMIZATION_GUIDE.md`)
2. Rebuild the image:
   ```bash
   docker build -t sakthipsgit/phi4-rag-combined-q3:latest .
   ```
3. Push to Docker Hub (if needed):
   ```bash
   docker push sakthipsgit/phi4-rag-combined-q3:latest
   ```

## 🔧 Key Changes to Apply

The optimized `api_server.py` should have these changes in the `load_models()` function:

```python
# Get CPU count, use all cores
import multiprocessing
cpu_count = os.cpu_count() or multiprocessing.cpu_count() or 4
n_threads = cpu_count

llm_model = Llama(
    model_path=model_path,
    n_ctx=512,  # Reduced from 2048
    n_threads=n_threads,  # Explicit threading
    n_gpu_layers=0,
    n_batch=512,
    use_mmap=True,
    use_mlock=False,
    n_threads_batch=n_threads,
    verbose=False
)
```

## 📊 Expected Results

- **Before**: 264 seconds (4.4 minutes)
- **After**: 30-60 seconds (0.5-1 minute)
- **Improvement**: 4-8x faster

## ✅ Validation

After applying changes, test with:
```
"What is the schema of dda_transactions?"
```

Should complete in 30-60 seconds instead of 264 seconds.

---

**Note**: The full optimized `api_server.py` code is documented in `CPU_OPTIMIZATION_GUIDE.md`.

