# 🚀 Quick Start - React UI Testing

## ✅ All Services Ready!

**Status Check**:
- ✅ **Phi-4 Q3 API**: Running on port `8083`
- ✅ **Spring Boot Backend**: Running on port `8080`
- ✅ **React Frontend**: Running on port `3000`
- ✅ **YugabyteDB**: Running on port `5433`

---

## 🎯 Open the UI Now!

### **👉 Open in Browser**: http://localhost:3000

The React chat interface should load immediately.

---

## 🧪 Quick Test Steps

### Step 1: Open UI
1. Open browser: http://localhost:3000
2. You should see the chat interface with welcome message

### Step 2: Enable Debug Mode (Optional)
1. Check the **"Show Debug Info"** checkbox in the header
2. This will show detected intents and retrieved documents

### Step 3: Ask Your First Question
1. Type in the input box: `What is the schema of dda_transactions?`
2. Click **"Send"** or press **Enter**
3. Wait for answer (2-5 minutes - CPU inference is slow!)

### Step 4: View Results
1. Answer will appear in chat
2. If debug mode is on, you'll see:
   - Detected document types
   - Retrieved documents with similarity scores
3. Click **"📚 Sources"** to see source documents

---

## 📝 Sample Questions to Try

### Schema Questions (Fast - Single Document)
- `What is the schema of dda_transactions?`
- `What are the columns in dda_transactions?`
- `What is the primary key of dda_transactions?`

### Business Metadata
- `What is the domain of dda_transactions?`
- `Who owns the dda_transactions table?`

### Lineage Questions
- `Which Kafka topic feeds dda_transactions?`
- `Which Spark job loads dda_transactions?`

### RCA Questions (Complex - Multiple Documents)
- `Why was dda_transactions delayed yesterday?`
- `What caused the Kafka lag?`
- `What was the root cause of yesterday's delay?`

---

## ⏱️ Expected Timing

| Operation | Time |
|-----------|------|
| UI Response | Instant |
| Intent Detection | < 100ms |
| Vector Search | 200-500ms |
| **LLM Generation** | **2-5 minutes** ⏳ |
| **Total** | **2-5 minutes** |

**Note**: CPU inference is very slow. Be patient! The first query may take longer.

---

## 🎨 What You'll See

### Chat Interface
- **Left Side**: Assistant messages (answers)
- **Right Side**: Your questions
- **Loading**: "Thinking..." indicator during processing
- **Sources**: Expandable section showing retrieved documents

### Debug Panel (if enabled)
- **Detected Types**: Shows which document types were searched
- **Retrieved Docs**: List of documents with similarity scores
- **Component Info**: Shows source component (Cassandra, Kafka, etc.)

---

## 🐛 Troubleshooting

### UI Not Loading
- Check: http://localhost:3000
- If blank, check browser console (F12)
- Restart React: `cd mvp/frontend && npm start`

### No Answer Received
- **Wait longer**: CPU inference takes 2-5 minutes
- Check Phi-4: `docker logs phi4-rag-api-q3 --tail 20`
- Check backend logs (terminal where Spring Boot is running)

### Error Messages
- Check browser console (F12 → Console tab)
- Check backend terminal for errors
- Verify all services are running (see status check above)

---

## ✅ Success Indicators

- ✅ UI loads without errors
- ✅ Questions can be submitted
- ✅ Loading indicator appears
- ✅ Answer is received (even if slow)
- ✅ Sources are displayed
- ✅ Debug info shows (if enabled)

---

## 🎉 You're Ready!

**👉 Open http://localhost:3000 and start testing!**

Remember: CPU inference is slow, so be patient. The first query may take 2-5 minutes, but it will work! 🚀

---

## 📊 Monitor Resources (Optional)

If you want to watch resource usage:

```bash
# Container memory
docker stats phi4-rag-api-q3 --no-stream

# System memory
vm_stat | head -5
```

---

**Happy Testing!** 🎊

