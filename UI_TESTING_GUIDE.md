# React UI Testing Guide - Full RAG Flow

## ✅ Current Status

### Services Running
- ✅ **Phi-4 Q3 API**: Running on port `8083`
- ✅ **Spring Boot Backend**: Running on port `8080` (detected)
- ✅ **React Frontend**: Running on port `3000` (detected)
- ✅ **YugabyteDB**: Running on port `5433`

---

## 🚀 Quick Start

### 1. Open React UI in Browser

**URL**: http://localhost:3000

The chat interface should load automatically.

---

## 🧪 Testing Scenarios

### Scenario 1: Schema Query (Fast Test)

**Question**: `What is the schema of dda_transactions?`

**Expected Flow**:
1. ✅ Intent Detection → `["METADATA"]`
2. ✅ Vector Search → Retrieves 1 METADATA document
3. ✅ LLM Generation → Returns schema information
4. ✅ **Total Time**: 2-5 minutes (mostly LLM inference)

**Expected Answer**: Should include:
- Column names (transaction_id, account_id, etc.)
- Primary key information
- TTL information

---

### Scenario 2: RCA Query (Multi-Document)

**Question**: `Why was dda_transactions delayed yesterday?`

**Expected Flow**:
1. ✅ Intent Detection → `["LOG_SUMMARY", "METRIC_SUMMARY", "LINEAGE"]`
2. ✅ Vector Search → Retrieves documents from all 3 types
3. ✅ LLM Generation → Combines information from all sources
4. ✅ **Total Time**: 3-6 minutes

**Expected Answer**: Should reference:
- Spark failures/errors (from LOG_SUMMARY)
- Latency metrics (from METRIC_SUMMARY)
- Pipeline information (from LINEAGE)

---

### Scenario 3: Lineage Query

**Question**: `Which Kafka topic feeds dda_transactions?`

**Expected Flow**:
1. ✅ Intent Detection → `["LINEAGE"]`
2. ✅ Vector Search → Retrieves LINEAGE documents
3. ✅ LLM Generation → Returns Kafka topic name
4. ✅ **Total Time**: 2-5 minutes

**Expected Answer**: Should mention:
- Kafka topic name (dda_txn_topic)
- Spark job name
- Data flow path

---

## 🎯 UI Features to Test

### 1. Debug Mode
- ✅ **Toggle**: Check "Show Debug Info" checkbox in header
- ✅ **Shows**: 
  - Detected document types
  - Retrieved documents with similarity scores
  - Component and source information

### 2. Message Display
- ✅ **User Messages**: Appear on right side
- ✅ **Assistant Messages**: Appear on left side
- ✅ **Loading Indicator**: Shows "Thinking..." during processing
- ✅ **Error Messages**: Display in red if something fails

### 3. Source Documents
- ✅ **Expandable**: Click "📚 Sources (N)" to see retrieved documents
- ✅ **Shows**: 
  - Source type (METADATA, LINEAGE, etc.)
  - Component (Cassandra, Kafka, etc.)
  - Similarity score (relevance percentage)
  - Document content

---

## 🔍 Verification Checklist

### Before Testing
- [ ] Phi-4 container is running: `docker ps | grep phi4`
- [ ] Backend is running: `curl http://localhost:8080/api/rag/health`
- [ ] Frontend is accessible: Open http://localhost:3000
- [ ] YugabyteDB is running: `docker ps | grep yugabyte`

### During Testing
- [ ] Questions are sent successfully
- [ ] Loading indicator appears
- [ ] Answers are received (even if slow)
- [ ] No error messages in browser console
- [ ] Debug info shows (if enabled)

### After Testing
- [ ] Answers are relevant to questions
- [ ] Sources are displayed correctly
- [ ] Similarity scores are shown
- [ ] Multiple questions work sequentially

---

## 🐛 Troubleshooting

### UI Not Loading

**Check**:
```bash
# Is React running?
lsof -i :3000

# Check React logs
# Look at terminal where you ran 'npm start'
```

**Fix**:
```bash
cd mvp/frontend
npm start
```

---

### Backend Not Responding

**Check**:
```bash
# Is Spring Boot running?
curl http://localhost:8080/api/rag/health

# Check backend logs
# Look at terminal where you ran Spring Boot
```

**Fix**:
```bash
cd mvp/backend
mvn spring-boot:run

# Or run JAR
java -jar target/rag-api-1.0.0.jar
```

---

### No Answer Received (Timeout)

**Possible Causes**:
1. **LLM inference is slow** (normal on CPU - 2-5 minutes)
2. **Phi-4 container crashed** (check: `docker ps | grep phi4`)
3. **Backend timeout** (check backend logs)

**Solutions**:
- Wait longer (CPU inference is slow)
- Check Phi-4 container: `docker logs phi4-rag-api-q3 --tail 50`
- Restart Phi-4: `docker restart phi4-rag-api-q3`
- Reduce `maxTokens` in request (backend config)

---

### Error: "Cannot connect to API"

**Check**:
```bash
# Is backend running?
curl http://localhost:8080/api/rag/health

# Is Phi-4 running?
curl http://localhost:8083/health
```

**Fix**:
- Start backend: `cd mvp/backend && mvn spring-boot:run`
- Start Phi-4: `docker start phi4-rag-api-q3`

---

### Empty Answer or "No information available"

**Possible Causes**:
1. No documents in database
2. Similarity threshold too high (filters out all docs)
3. Wrong table/keyspace filter

**Solutions**:
- Check database has data: `SELECT COUNT(*) FROM rag_documents;`
- Lower similarity threshold in `application.yml`
- Verify table/keyspace names match

---

## 📊 Expected Performance

| Operation | Time | Notes |
|-----------|------|-------|
| UI Load | < 1s | Fast |
| Intent Detection | < 100ms | Fast |
| Vector Search | 200-500ms | Fast |
| Embedding Generation | 200-500ms | Fast |
| **LLM Inference** | **2-5 min** | **CPU is slow** |
| **Total Query** | **2-5 min** | Mostly LLM time |

**Note**: CPU inference is very slow. Be patient! ⏱️

---

## 🎨 UI Screenshots Guide

### What You Should See

1. **Header**:
   - Title: "🤖 RAG Chat - Data Platform Assistant"
   - "Show Debug Info" checkbox
   - "Clear Chat" button

2. **Welcome Message** (if no messages):
   - Example questions listed
   - Instructions

3. **Chat Messages**:
   - Your questions on right
   - Assistant answers on left
   - Loading indicator during processing

4. **Debug Panel** (if enabled):
   - Detected document types
   - Retrieved documents list
   - Similarity scores

---

## 🔄 Testing Workflow

### Step 1: Open UI
1. Navigate to http://localhost:3000
2. Verify UI loads correctly
3. Enable "Show Debug Info" (optional)

### Step 2: Test Simple Query
1. Type: `What is the schema of dda_transactions?`
2. Click "Send" or press Enter
3. Wait for answer (2-5 minutes)
4. Verify answer contains schema info

### Step 3: Test Complex Query
1. Type: `Why was dda_transactions delayed yesterday?`
2. Click "Send"
3. Wait for answer (3-6 minutes)
4. Check debug info shows multiple document types
5. Verify answer combines multiple sources

### Step 4: Test Multiple Queries
1. Ask 2-3 different questions
2. Verify each works independently
3. Check message history is maintained
4. Verify no memory leaks (container stays stable)

---

## 📝 Sample Questions to Test

### Schema Questions
- `What is the schema of dda_transactions?`
- `What are the columns in dda_transactions?`
- `What is the primary key of dda_transactions?`

### Business Metadata Questions
- `What is the domain of dda_transactions?`
- `Who owns the dda_transactions table?`
- `Is dda_transactions a PII table?`

### Lineage Questions
- `Which Kafka topic feeds dda_transactions?`
- `Which Spark job loads dda_transactions?`
- `Which API reads from dda_transactions?`

### RCA Questions
- `Why was dda_transactions delayed yesterday?`
- `What caused the Kafka lag?`
- `What was the root cause of yesterday's delay?`

### Metric Questions
- `What was the latency for dda_transactions yesterday?`
- `How high did Kafka lag go?`
- `What was the worst performance day this week?`

---

## ✅ Success Criteria

### UI Functionality
- ✅ UI loads without errors
- ✅ Questions can be submitted
- ✅ Answers are displayed
- ✅ Debug info works (if enabled)
- ✅ Multiple questions work sequentially

### Answer Quality
- ✅ Answers are relevant to questions
- ✅ Answers reference retrieved documents
- ✅ No hallucinations (made-up information)
- ✅ Answers are formatted clearly

### Performance
- ✅ Embedding generation < 1 second
- ✅ Vector search < 1 second
- ✅ LLM inference completes (even if slow)
- ✅ No timeouts or crashes

---

## 🚨 Important Notes

1. **CPU Inference is Slow**: Expect 2-5 minutes per query. This is normal!
2. **Be Patient**: Don't refresh or close browser during inference
3. **Monitor Memory**: Watch Docker stats if queries fail
4. **Start Small**: Test with simple questions first
5. **Check Logs**: If something fails, check browser console and backend logs

---

## 🎯 Quick Test Commands

### Verify All Services
```bash
# Phi-4
curl http://localhost:8083/health

# Backend
curl http://localhost:8080/api/rag/health

# Frontend
curl http://localhost:3000
```

### Monitor Resources
```bash
# Container memory
docker stats phi4-rag-api-q3 --no-stream

# System memory
vm_stat | head -5
```

### Check Logs
```bash
# Phi-4 logs
docker logs phi4-rag-api-q3 --tail 20

# Backend logs (check terminal where Spring Boot is running)
# Frontend logs (check terminal where React is running)
```

---

## 🎉 You're Ready!

**Open your browser and navigate to**: http://localhost:3000

**Start testing!** Ask questions and watch the magic happen. 🚀

Remember: CPU inference is slow, so be patient. The first query may take 2-5 minutes, but subsequent queries should work similarly.

Good luck! 🍀

