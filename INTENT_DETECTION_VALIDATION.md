# Intent Detection Implementation Validation

## ❌ Current Status: JSON-Based Approach NOT Implemented

### Findings:

1. **❌ `rag-intents.json` file does NOT exist**
   - Expected location: `mvp/backend/src/main/resources/rag-intents.json`
   - Status: **MISSING**

2. **❌ `IntentConfigLoader` component does NOT exist**
   - Expected: Spring `@Component` to load JSON at startup
   - Status: **MISSING**

3. **✅ Intent Detection Logic EXISTS but uses hardcoded Maps**
   - Current: Static HashMaps in `IntentDetectionService.java`
   - Keywords hardcoded in Java code
   - Not configurable without code changes

### Current Implementation:

**File:** `mvp/backend/src/main/java/com/yugabyte/rag/service/IntentDetectionService.java`

**Approach:**
- Uses static `HashMap<String, List<String>> INTENT_DOC_TYPE_MAP`
- Uses static `HashMap<String, String> KEYWORD_TO_DOC_SUB_TYPE`
- Keywords hardcoded in static initializer blocks
- Not JSON-driven

**Limitations:**
- ❌ Cannot update intents without code changes
- ❌ Cannot version intent rules
- ❌ Harder to maintain and extend
- ❌ No machine-readable intent configuration

---

## ✅ What Needs to Be Implemented

### 1. Create `rag-intents.json` file
**Location:** `mvp/backend/src/main/resources/rag-intents.json`

### 2. Create `IntentConfigLoader` component
**Location:** `mvp/backend/src/main/java/com/yugabyte/rag/config/IntentConfigLoader.java`

### 3. Refactor `IntentDetectionService` to use JSON
- Load intents from JSON at startup
- Use JSON-driven keyword matching
- Support time_window_days for time-scoped intents

### 4. Create DTOs for Intent Rules
- `IntentRule.java` - Represents one intent rule
- `DetectedIntent.java` - Represents detected intent result

---

## 📊 Comparison: Current vs. Proposed

| Feature | Current (Hardcoded) | Proposed (JSON) |
|---------|-------------------|-----------------|
| **Configuration** | Java code | JSON file |
| **Maintainability** | Requires code changes | Edit JSON file |
| **Versioning** | Git code changes | JSON file versioning |
| **Runtime Updates** | Requires restart + rebuild | Can hot-reload (if implemented) |
| **Machine Readable** | ❌ No | ✅ Yes |
| **Intent Names** | ❌ No explicit names | ✅ Yes (BUSINESS_METADATA, etc.) |
| **Time Windows** | Hardcoded logic | ✅ JSON configurable |
| **Multi-Intent Support** | ✅ Yes | ✅ Yes |

---

## 🎯 Recommendation

**Implement the JSON-based approach** because:
1. ✅ Production-ready pattern
2. ✅ Easier maintenance
3. ✅ Version control friendly
4. ✅ Machine-readable for automation
5. ✅ Supports time_window_days configuration
6. ✅ Explicit intent names for debugging

---

**Status:** Validation Complete - JSON Approach NOT Implemented  
**Action Required:** Implement JSON-based intent detection

