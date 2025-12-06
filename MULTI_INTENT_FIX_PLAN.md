# Multi-Intent Query Fix Plan - Review & Proposed Changes

## 📊 Current Problem Analysis

### What's Working ✅
1. **Intent Detection**: Correctly identifies 3 intents (METADATA, LOG_SUMMARY, METRIC_SUMMARY)
2. **Per-Intent Query Rewriting**: Each intent gets its own rewritten query ✅
3. **Per-Intent Embedding Generation**: 3 separate embeddings generated ✅
4. **Per-Intent Vector Search**: 3 separate searches, all returning documents ✅
   - METADATA: similarity 0.944
   - LOG_SUMMARY: similarity 0.801
   - METRIC_SUMMARY: similarity 0.929
5. **Prompt Construction**: Successfully combines all 3 documents (2364 chars)

### What's Broken ❌
1. **LLM Generation**: Takes 187 seconds and returns **0 characters** (empty answer)
2. **Total Latency**: 188 seconds (~3 minutes) - unacceptable
3. **No Error Handling**: Silent failure, no retry or fallback
4. **Single LLM Call**: One large prompt for all intents confuses the model

---

## 🔍 Root Cause Analysis

### Primary Issues

1. **Single LLM Call with Multi-Intent Prompt**
   - Current: One prompt with all 3 intents combined (2364 chars)
   - Problem: Model gets confused, doesn't know how to structure multi-part answer
   - Result: Empty output or timeout

2. **Low maxTokens (100)**
   - Current: `maxTokens=100`
   - Problem: Too small for multi-intent queries (needs ~200-300 tokens)
   - Result: Truncated or incomplete answers

3. **CPU Inference Latency**
   - Current: 187 seconds for LLM generation
   - Problem: CPU-only inference is inherently slow
   - Impact: Even with fixes, will still be slow (but should work)

4. **No Timeout/Retry Logic**
   - Current: Waits indefinitely, no fallback
   - Problem: If LLM fails, entire request fails silently
   - Result: Empty response to user

5. **Prompt Structure for Multi-Intent**
   - Current: Generic prompt, doesn't explicitly separate intents
   - Problem: Model doesn't understand it needs to answer 3 separate questions
   - Result: Confusion, empty output

---

## 🎯 Proposed Solution Architecture

### High-Level Flow Change

**Current Flow:**
```
Intent Detection → Per-Intent Search → Single Combined Prompt → Single LLM Call → Response
```

**Proposed Flow:**
```
Intent Detection → Per-Intent Search → Per-Intent Prompts → Per-Intent LLM Calls (Parallel) → Aggregate Results → Response
```

---

## 📝 Detailed Change Plan

### Change 1: Per-Intent LLM Calls (CRITICAL - P0)

**Location**: `VectorSearchService.java` → `callPhi4()` method

**Current Implementation**:
```java
// Single LLM call with all documents
String prompt = promptBuilder.buildPrompt(question, allDocuments);
String answer = phi4Client.generateRagAnswer(query, prompt, maxTokens, temperature);
```

**Proposed Implementation**:
```java
// Per-intent LLM calls
Map<String, String> intentAnswers = new HashMap<>();
for (String docType : docTypes) {
    // Get documents for this intent only
    List<SourceDocument> intentDocs = filterDocumentsByType(allDocuments, docType);
    
    // Build intent-specific prompt
    String intentPrompt = promptBuilder.buildIntentPrompt(question, intentDocs, docType);
    
    // Call LLM for this intent
    String intentAnswer = phi4Client.generateRagAnswer(
        extractIntentQuestion(question, docType), 
        intentPrompt, 
        maxTokens, // Increased to 256
        temperature
    );
    
    intentAnswers.put(docType, intentAnswer);
}

// Aggregate results
String finalAnswer = aggregateIntentAnswers(intentAnswers, docTypes);
```

**Benefits**:
- ✅ Smaller prompts per intent (faster generation)
- ✅ Clearer instructions per intent (better answers)
- ✅ Can parallelize if needed
- ✅ Guaranteed answer per intent

**Expected Impact**: 
- Reliability: 0% → 100% (guaranteed answers)
- Latency: 187s → 60-90s (3 smaller calls vs 1 large call)

---

### Change 2: Increase maxTokens (CRITICAL - P0)

**Location**: `RagController.java` → `ask()` method

**Current**:
```java
Integer maxTokens = 100; // Too small
```

**Proposed**:
```java
// Dynamic maxTokens based on number of intents
int numIntents = detectedIntents.size();
int maxTokens = Math.min(512, 200 + (numIntents * 50)); // 200 base + 50 per intent
// Single intent: 200 tokens
// 3 intents: 350 tokens
// Cap at 512 for safety
```

**Benefits**:
- ✅ Enough tokens for complete answers
- ✅ Prevents truncation
- ✅ Scales with number of intents

**Expected Impact**: 
- Answer Quality: Incomplete → Complete
- Reliability: Better answers, less truncation

---

### Change 3: Add Timeout & Retry Logic (HIGH - P1)

**Location**: `Phi4Client.java` → `generateRagAnswer()` method

**Current**:
```java
// No timeout, no retry
String answer = webClient.post()
    .uri(ragUrl)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(RagGenerateResponse.class)
    .timeout(Duration.ofMillis(timeout)) // 10 minutes - too long
    .block();
```

**Proposed**:
```java
// Per-intent timeout (30-60 seconds)
Duration intentTimeout = Duration.ofSeconds(60);
int maxRetries = 2;

String answer = null;
for (int attempt = 0; attempt <= maxRetries; attempt++) {
    try {
        answer = webClient.post()
            .uri(ragUrl)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(RagGenerateResponse.class)
            .timeout(intentTimeout) // 60 seconds per intent
            .block();
        
        if (answer != null && !answer.trim().isEmpty()) {
            break; // Success
        }
    } catch (TimeoutException e) {
        log.warn("LLM timeout for intent {}, attempt {}", docType, attempt);
        if (attempt == maxRetries) {
            // Fallback: return top document content
            answer = getFallbackAnswer(intentDocs);
        }
    }
}
```

**Benefits**:
- ✅ Prevents hanging requests
- ✅ Automatic retry on failure
- ✅ Fallback to document content if LLM fails

**Expected Impact**:
- Reliability: 0% → 95%+ (with fallback)
- User Experience: No more empty responses

---

### Change 4: Optimize Prompt Structure for Multi-Intent (MEDIUM - P1)

**Location**: `PromptBuilderService.java` → `buildIntentPrompt()` method (new)

**Current**:
```java
// Generic prompt for all intents
String prompt = "Context: " + context + "\n\nQ: " + query + "\nA:";
```

**Proposed**:
```java
// Intent-specific prompt structure
public String buildIntentPrompt(String question, List<SourceDocument> docs, String docType) {
    String intentInstruction = getIntentInstruction(docType);
    String context = formatDocuments(docs);
    
    return String.format(
        "You are a data platform assistant. Answer the following question using ONLY the provided context.\n\n" +
        "Intent: %s\n" +
        "Question: %s\n\n" +
        "Context:\n%s\n\n" +
        "Instructions: %s\n\n" +
        "Answer (2-4 sentences, be specific):",
        docType, question, context, intentInstruction
    );
}

private String getIntentInstruction(String docType) {
    switch (docType) {
        case "METADATA":
            return "Provide the schema definition including primary key, clustering columns, and all column types.";
        case "LOG_SUMMARY":
            return "List the errors and failures that occurred, including timestamps and error messages.";
        case "METRIC_SUMMARY":
            return "Provide current metric values and indicate if they are within normal range.";
        default:
            return "Answer based on the provided context.";
    }
}
```

**Benefits**:
- ✅ Clear instructions per intent type
- ✅ Model knows exactly what to answer
- ✅ Better structured responses

**Expected Impact**:
- Answer Quality: Better structured, more accurate
- Reliability: Less confusion, better answers

---

### Change 5: Parallel Execution (OPTIONAL - P2)

**Location**: `VectorSearchService.java` → `searchVectors()` method

**Current**:
```java
// Sequential per-intent LLM calls
for (String docType : docTypes) {
    String answer = callLLM(...); // Blocks until complete
}
```

**Proposed**:
```java
// Parallel per-intent LLM calls (if multiple intents)
if (docTypes.size() > 1) {
    List<CompletableFuture<IntentAnswer>> futures = docTypes.stream()
        .map(docType -> CompletableFuture.supplyAsync(() -> {
            return callLLMForIntent(docType, ...);
        }, executorService))
        .collect(Collectors.toList());
    
    // Wait for all to complete
    List<IntentAnswer> answers = futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());
} else {
    // Single intent - no need for parallel
    String answer = callLLMForIntent(docTypes.get(0), ...);
}
```

**Benefits**:
- ✅ 3x faster for 3 intents (if CPU cores available)
- ✅ Better resource utilization

**Expected Impact**:
- Latency: 60-90s → 20-30s (for 3 intents in parallel)
- **Note**: Only beneficial if multiple CPU cores available

---

### Change 6: Result Aggregation (CRITICAL - P0)

**Location**: `VectorSearchService.java` → New method `aggregateIntentAnswers()`

**Proposed**:
```java
private String aggregateIntentAnswers(Map<String, String> intentAnswers, List<String> docTypes) {
    StringBuilder aggregated = new StringBuilder();
    
    for (String docType : docTypes) {
        String answer = intentAnswers.get(docType);
        if (answer != null && !answer.trim().isEmpty()) {
            String sectionHeader = getSectionHeader(docType);
            aggregated.append(sectionHeader).append("\n");
            aggregated.append(answer).append("\n\n");
        }
    }
    
    if (aggregated.length() == 0) {
        return "I apologize, but I was unable to generate answers for your query. " +
               "Please try rephrasing your question or check if the relevant data is available.";
    }
    
    return aggregated.toString();
}

private String getSectionHeader(String docType) {
    switch (docType) {
        case "METADATA":
            return "**Schema Information:**";
        case "LOG_SUMMARY":
            return "**Recent Errors (Last 24 Hours):**";
        case "METRIC_SUMMARY":
            return "**Current Metrics:**";
        default:
            return "**" + docType + ":**";
    }
}
```

**Benefits**:
- ✅ Structured multi-part answer
- ✅ Clear separation between intents
- ✅ User-friendly format

**Expected Impact**:
- User Experience: Clear, structured answers
- Readability: Much better than single blob

---

## 📊 Expected Improvements Summary

| Metric | Current | After Changes | Improvement |
|--------|---------|---------------|-------------|
| **Answer Reliability** | 0% (empty) | 95%+ (with fallback) | ✅ **Fixed** |
| **Total Latency** | 187s | 60-90s (sequential) / 20-30s (parallel) | ✅ **2-3x faster** |
| **Answer Quality** | Empty | Complete per intent | ✅ **Much better** |
| **Error Handling** | None | Timeout + Retry + Fallback | ✅ **Robust** |
| **User Experience** | Empty response | Structured multi-part answer | ✅ **Excellent** |

---

## 🎯 Implementation Priority

### P0 (Critical - Do First)
1. ✅ **Per-Intent LLM Calls** - Fixes empty answer issue
2. ✅ **Increase maxTokens** - Prevents truncation
3. ✅ **Result Aggregation** - Structures the response

### P1 (High - Do Next)
4. ✅ **Timeout & Retry Logic** - Improves reliability
5. ✅ **Optimize Prompt Structure** - Better answers

### P2 (Optional - Nice to Have)
6. ✅ **Parallel Execution** - Additional speedup (if resources allow)

---

## 🔧 Files to Modify

1. **`VectorSearchService.java`**
   - Add `callLLMForIntent()` method
   - Modify `callPhi4()` to iterate per intent
   - Add `aggregateIntentAnswers()` method
   - Add parallel execution (optional)

2. **`PromptBuilderService.java`**
   - Add `buildIntentPrompt()` method
   - Add `getIntentInstruction()` helper
   - Keep existing `buildPrompt()` for single-intent

3. **`Phi4Client.java`**
   - Add timeout configuration per call
   - Add retry logic
   - Add fallback mechanism

4. **`RagController.java`**
   - Update `maxTokens` calculation (dynamic based on intents)

---

## 📝 Implementation Notes

### Backward Compatibility
- Single-intent queries should continue to work as before
- Only multi-intent queries use the new per-intent LLM call flow
- Existing prompt builder can be kept for single-intent

### Testing Strategy
1. Test single-intent query (should work as before)
2. Test multi-intent query (should return structured answer)
3. Test timeout scenario (should retry and fallback)
4. Test with 1, 2, 3+ intents (should scale)

### Performance Considerations
- Per-intent calls are smaller and faster than one large call
- Parallel execution only if multiple CPU cores available
- Fallback to document content if LLM fails (fast)

---

## ✅ Success Criteria

After implementation:
- ✅ Multi-intent queries return structured answers (not empty)
- ✅ Total latency < 90 seconds (sequential) or < 30 seconds (parallel)
- ✅ Each intent gets a clear answer section
- ✅ Timeout/retry prevents hanging requests
- ✅ Fallback ensures user always gets some response

---

## 🚀 Next Steps

1. **Review this plan** - Confirm approach is correct
2. **Implement P0 changes** - Per-intent LLM calls, maxTokens, aggregation
3. **Test with multi-intent query** - Verify it works
4. **Add P1 improvements** - Timeout, retry, prompt optimization
5. **Consider P2** - Parallel execution if needed

---

**Status**: 📋 **Plan Ready for Review**  
**Priority**: 🔥 **P0 - Critical** (Fixes empty answer issue)  
**Estimated Effort**: 4-6 hours for P0+P1 changes

