package com.yugabyte.rag.controller;

import com.yugabyte.rag.model.*;
import com.yugabyte.rag.service.IntentDetectionService;
import com.yugabyte.rag.service.RagService;
import com.yugabyte.rag.service.VectorSearchService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
@CrossOrigin(origins = "*")  // Allow React UI
@Slf4j
public class RagController {
    
    private final RagService ragService;
    private final IntentDetectionService intentService;
    private final VectorSearchService vectorService;
    
    public RagController(RagService ragService, 
                        IntentDetectionService intentService,
                        VectorSearchService vectorService) {
        this.ragService = ragService;
        this.intentService = intentService;
        this.vectorService = vectorService;
    }
    
    /**
     * Main endpoint: /api/rag/ask
     * Full RAG flow: Intent Detection -> Vector Search -> Prompt Building -> Phi-4 Generation
     * Returns simple string answer (for UI display)
     */
    @PostMapping("/ask")
    public ResponseEntity<String> askQuestion(@Valid @RequestBody AskRequest request) {
        long requestStartTime = System.currentTimeMillis();
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔵 [REQUEST-{}] Step 1️⃣: User Question Received [{}]", 
                requestId, LocalDateTime.now().format(formatter));
        log.info("   Question: '{}'", request.getQuestion());
        log.info("   Table: {}, Keyspace: {}, TopK: {}", 
                request.getTable(), request.getKeyspace(), request.getTopK());
        
        try {
            // Step 3: Intent Detection
            long step3Start = System.currentTimeMillis();
            log.info("🔵 [REQUEST-{}] Step 3️⃣: Intent Detection - STARTED [{}]", 
                    requestId, LocalDateTime.now().format(formatter));
            List<String> docTypes = intentService.detectIntents(request.getQuestion());
            long step3Duration = System.currentTimeMillis() - step3Start;
            log.info("✅ [REQUEST-{}] Step 3️⃣: Intent Detection - COMPLETED [{}] (Duration: {}ms)", 
                    requestId, LocalDateTime.now().format(formatter), step3Duration);
            log.info("   Detected source_types: {}", docTypes);
            
            // Detect doc_sub_type for detailed logging
            String detectedDocSubType = null;
            for (String docType : docTypes) {
                String subType = intentService.detectDocSubType(request.getQuestion(), docType);
                if (subType != null) {
                    detectedDocSubType = subType;
                    log.info("   Detected doc_sub_type: {}", subType);
                    break;
                }
            }
            
            // Step 4: Query Rewriting (happens inside VectorSearchService, but we log it here)
            log.info("🔵 [REQUEST-{}] Step 4️⃣: Query Rewriting - Will be logged by VectorSearchService [{}]", 
                    requestId, LocalDateTime.now().format(formatter));
            
            // Step 5-6: Vector Search + similarity filtering
            long step5Start = System.currentTimeMillis();
            log.info("🔵 [REQUEST-{}] Step 5️⃣-6️⃣: Vector Search - STARTED [{}]", 
                    requestId, LocalDateTime.now().format(formatter));
            List<RagQueryResponse.SourceDocument> retrievedDocs = vectorService.searchVectors(
                request.getQuestion(),
                docTypes,
                request.getTable(),
                request.getKeyspace(),
                null,  // clusterName - can be added to request if needed
                request.getTopK(),
                null   // daysBack - will default to 180 in service
            );
            long step5Duration = System.currentTimeMillis() - step5Start;
            log.info("✅ [REQUEST-{}] Step 5️⃣-6️⃣: Vector Search - COMPLETED [{}] (Duration: {}ms)", 
                    requestId, LocalDateTime.now().format(formatter), step5Duration);
            log.info("   Retrieved {} documents after similarity filtering", retrievedDocs.size());
            if (!retrievedDocs.isEmpty()) {
                log.info("   Top document: doc_sub_type={}, similarity={}, source={}", 
                        retrievedDocs.get(0).getDocSubType(),
                        retrievedDocs.get(0).getSimilarityScore(),
                        retrievedDocs.get(0).getSourceName());
            }
            
            // Step 7: Candidate Document Selection (already done in vector search)
            log.info("✅ [REQUEST-{}] Step 7️⃣: Candidate Selection - COMPLETED [{}] (included in Step 5-6)", 
                    requestId, LocalDateTime.now().format(formatter));
            
            // Step 8: Prompt Construction (only for single-intent queries)
            // ✅ OPTIMIZATION: Skip prompt construction for multi-intent (uses per-intent prompts)
            boolean isMultiIntent = docTypes.size() > 1;
            String structuredPrompt = null;
            
            if (!isMultiIntent) {
                long step8Start = System.currentTimeMillis();
                log.info("🔵 [REQUEST-{}] Step 8️⃣: Prompt Construction - STARTED [{}]", 
                        requestId, LocalDateTime.now().format(formatter));
                structuredPrompt = ragService.buildStructuredPrompt(request.getQuestion(), retrievedDocs);
                long step8Duration = System.currentTimeMillis() - step8Start;
                log.info("✅ [REQUEST-{}] Step 8️⃣: Prompt Construction - COMPLETED [{}] (Duration: {}ms)", 
                        requestId, LocalDateTime.now().format(formatter), step8Duration);
                log.info("   Prompt length: {} characters", structuredPrompt.length());
                log.debug("   Prompt preview (first 200 chars): {}", 
                        structuredPrompt.substring(0, Math.min(200, structuredPrompt.length())));
            } else {
                log.info("🔵 [REQUEST-{}] Step 8️⃣: Prompt Construction - SKIPPED [{}] (multi-intent uses per-intent prompts)", 
                        requestId, LocalDateTime.now().format(formatter));
            }
            
            // Step 9: Call Flask Phi-4 API
            long step9Start = System.currentTimeMillis();
            log.info("🔵 [REQUEST-{}] Step 9️⃣: Phi-4 LLM Generation - STARTED [{}]", 
                    requestId, LocalDateTime.now().format(formatter));
            
            // Calculate dynamic maxTokens based on number of intents
            Integer calculatedMaxTokens = calculateMaxTokens(request.getMaxTokens(), docTypes.size());
            log.info("   Calling Phi-4 API: maxTokens={} (calculated from {} intents), temperature={}", 
                    calculatedMaxTokens, docTypes.size(), request.getTemperature());
            
            String phi4Response = vectorService.callPhi4(
                structuredPrompt,
                request.getQuestion(),
                retrievedDocs,
                docTypes,
                calculatedMaxTokens,
                request.getTemperature()
            );
            long step9Duration = System.currentTimeMillis() - step9Start;
            log.info("✅ [REQUEST-{}] Step 9️⃣: Phi-4 LLM Generation - COMPLETED [{}] (Duration: {}ms)", 
                    requestId, LocalDateTime.now().format(formatter), step9Duration);
            log.info("   Answer length: {} characters", phi4Response.length());
            log.info("   Answer preview: {}", 
                    phi4Response.substring(0, Math.min(150, phi4Response.length())));
            
            // Step 10-11: Response ready
            long totalDuration = System.currentTimeMillis() - requestStartTime;
            log.info("✅ [REQUEST-{}] Step 🔟-1️⃣1️⃣: Response Ready - COMPLETED [{}]", 
                    requestId, LocalDateTime.now().format(formatter));
            log.info("═══════════════════════════════════════════════════════════════");
            String startTimeStr = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(requestStartTime), 
                java.time.ZoneId.systemDefault()
            ).format(formatter);
            log.info("✅ [REQUEST-{}] TOTAL REQUEST TIME: {}ms [Started: {}, Completed: {}]", 
                    requestId, totalDuration, startTimeStr, LocalDateTime.now().format(formatter));
            log.info("   Breakdown: Intent={}ms, VectorSearch={}ms, Prompt={}ms, LLM={}ms", 
                    step3Duration, step5Duration, step8Duration, step9Duration);
            log.info("═══════════════════════════════════════════════════════════════");
            
            // Step 11: Return Phi-4 answer
            return ResponseEntity.ok(phi4Response);
            
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - requestStartTime;
            log.error("❌ [REQUEST-{}] ERROR after {}ms: {}", requestId, totalDuration, e.getMessage(), e);
            log.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(500)
                .body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Calculate dynamic maxTokens based on number of intents.
     * For multi-intent queries, we need more tokens per intent.
     * 
     * @param requestedMaxTokens User-requested maxTokens (may be null)
     * @param numIntents Number of detected intents
     * @return Calculated maxTokens
     */
    private Integer calculateMaxTokens(Integer requestedMaxTokens, int numIntents) {
        if (requestedMaxTokens != null && requestedMaxTokens > 0) {
            // User specified, but ensure minimum for multi-intent
            if (numIntents > 1) {
                return Math.max(requestedMaxTokens, 200); // At least 200 for multi-intent
            }
            return requestedMaxTokens;
        }
        
        // Default calculation: 200 base + 50 per intent, cap at 512
        int calculated = 200 + (numIntents * 50);
        return Math.min(calculated, 512);
    }
    
    /**
     * Enhanced endpoint: /api/rag/ask-detailed
     * Returns detailed step-by-step progress for debugging and validation
     */
    @PostMapping("/ask-detailed")
    public ResponseEntity<RagProgressResponse> askQuestionDetailed(@Valid @RequestBody AskRequest request) {
        long requestStartTime = System.currentTimeMillis();
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        List<RagProgressResponse.StepProgress> steps = new ArrayList<>();
        
        try {
            // Step 1-2: User Question
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(1)
                .stepName("User Question Received")
                .description("Question received from React UI")
                .status("COMPLETED")
                .durationMs(0L)
                .input(Map.of("question", request.getQuestion(), 
                             "table", request.getTable() != null ? request.getTable() : "default",
                             "keyspace", request.getKeyspace() != null ? request.getKeyspace() : "default"))
                .output(Map.of("requestId", requestId))
                .build());
            
            // Step 3: Intent Detection
            long step3Start = System.currentTimeMillis();
            List<String> docTypes = intentService.detectIntents(request.getQuestion());
            String detectedDocSubType = null;
            for (String docType : docTypes) {
                String subType = intentService.detectDocSubType(request.getQuestion(), docType);
                if (subType != null) {
                    detectedDocSubType = subType;
                    break;
                }
            }
            long step3Duration = System.currentTimeMillis() - step3Start;
            
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(3)
                .stepName("Intent Detection")
                .description("Detected document types from question using rag-intents.json and example_questions")
                .status("COMPLETED")
                .durationMs(step3Duration)
                .input(Map.of("question", request.getQuestion()))
                .output(Map.of("source_types", docTypes, 
                             "doc_sub_type", detectedDocSubType != null ? detectedDocSubType : "not_detected"))
                .build());
            
            // Step 4: Query Rewriting (will be logged in VectorSearchService)
            // We'll capture it from the vector search step
            
            // Step 5-6: Vector Search
            long step5Start = System.currentTimeMillis();
            List<RagQueryResponse.SourceDocument> retrievedDocs = vectorService.searchVectors(
                request.getQuestion(),
                docTypes,
                request.getTable(),
                request.getKeyspace(),
                null,  // clusterName
                request.getTopK(),
                null   // daysBack - will default to 180 in service
            );
            long step5Duration = System.currentTimeMillis() - step5Start;
            
            // Extract rewritten query from logs (we'll add it to VectorSearchService response)
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(4)
                .stepName("Query Rewriting")
                .description("Rewrote user question using canonical template from query-rewrite-templates.json")
                .status("COMPLETED")
                .durationMs(0L)
                .input(Map.of("original_question", request.getQuestion(),
                             "doc_sub_type", detectedDocSubType != null ? detectedDocSubType : "unknown"))
                .output(Map.of("note", "See VectorSearchService logs for rewritten query"))
                .build());
            
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(5)
                .stepName("Embedding Generation")
                .description("Generated vector embedding for rewritten query using embedding API")
                .status("COMPLETED")
                .durationMs(0L)
                .input(Map.of("note", "Embedding generation time included in vector search"))
                .output(Map.of("embedding_dimension", 384, "note", "Check VectorSearchService logs for timing"))
                .build());
            
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(6)
                .stepName("Vector Search & Filtering")
                .description("Searched Yugabyte PGVector with per-doc-type similarity thresholds")
                .status("COMPLETED")
                .durationMs(step5Duration)
                .input(Map.of("doc_types", docTypes,
                             "table", request.getTable() != null ? request.getTable() : "default",
                             "topK", request.getTopK() != null ? request.getTopK() : 6))
                .output(Map.of("retrieved_count", retrievedDocs.size(),
                             "documents", retrievedDocs.stream()
                                 .map(doc -> Map.of(
                                     "doc_sub_type", doc.getDocSubType() != null ? doc.getDocSubType() : "unknown",
                                     "similarity", doc.getSimilarityScore() != null ? doc.getSimilarityScore() : 0.0,
                                     "source_name", doc.getSourceName() != null ? doc.getSourceName() : "unknown"
                                 ))
                                 .limit(3)
                                 .collect(java.util.stream.Collectors.toList())))
                .build());
            
            // Step 7: Candidate Selection (already done)
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(7)
                .stepName("Candidate Document Selection")
                .description("Filtered and sorted documents by similarity score")
                .status("COMPLETED")
                .durationMs(0L)
                .input(Map.of("total_retrieved", retrievedDocs.size()))
                .output(Map.of("selected_count", retrievedDocs.size(),
                             "sorted_by", "similarity_desc"))
                .build());
            
            // Step 8: Prompt Construction
            long step8Start = System.currentTimeMillis();
            String structuredPrompt = ragService.buildStructuredPrompt(request.getQuestion(), retrievedDocs);
            long step8Duration = System.currentTimeMillis() - step8Start;
            
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(8)
                .stepName("Prompt Construction")
                .description("Built structured prompt with system prompt, question, and retrieved documents")
                .status("COMPLETED")
                .durationMs(step8Duration)
                .input(Map.of("question", request.getQuestion(),
                             "document_count", retrievedDocs.size()))
                .output(Map.of("prompt_length", structuredPrompt.length(),
                             "prompt_preview", structuredPrompt.substring(0, Math.min(300, structuredPrompt.length()))))
                .build());
            
            // Step 9: Phi-4 Generation
            long step9Start = System.currentTimeMillis();
            
            // Calculate dynamic maxTokens based on number of intents
            Integer calculatedMaxTokens = calculateMaxTokens(request.getMaxTokens(), docTypes.size());
            
            String phi4Response = vectorService.callPhi4(
                structuredPrompt,
                request.getQuestion(),
                retrievedDocs,
                docTypes,
                calculatedMaxTokens,
                request.getTemperature()
            );
            long step9Duration = System.currentTimeMillis() - step9Start;
            
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(9)
                .stepName("Phi-4 LLM Generation")
                .description("Generated answer using Phi-4 Q3 model with structured prompt")
                .status("COMPLETED")
                .durationMs(step9Duration)
                .input(Map.of("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 100,
                             "temperature", request.getTemperature() != null ? request.getTemperature() : 0.3,
                             "prompt_length", structuredPrompt.length()))
                .output(Map.of("answer_length", phi4Response.length(),
                             "answer", phi4Response))
                .build());
            
            // Step 10-11: Response
            long totalDuration = System.currentTimeMillis() - requestStartTime;
            Map<String, Object> summary = new HashMap<>();
            summary.put("total_duration_ms", totalDuration);
            summary.put("intent_detection_ms", step3Duration);
            summary.put("vector_search_ms", step5Duration);
            summary.put("prompt_building_ms", step8Duration);
            summary.put("llm_generation_ms", step9Duration);
            summary.put("documents_retrieved", retrievedDocs.size());
            summary.put("request_id", requestId);
            
            RagProgressResponse response = RagProgressResponse.builder()
                .answer(phi4Response)
                .steps(steps)
                .summary(summary)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            steps.add(RagProgressResponse.StepProgress.builder()
                .stepNumber(-1)
                .stepName("ERROR")
                .description("Error occurred during processing")
                .status("ERROR")
                .error(e.getMessage())
                .build());
            
            RagProgressResponse errorResponse = RagProgressResponse.builder()
                .answer("Error: " + e.getMessage())
                .steps(steps)
                .summary(Map.of("error", e.getMessage()))
                .build();
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Endpoint: /api/rag/detect-intent
     * Detect document types from question (Step 1-2)
     */
    @PostMapping("/detect-intent")
    public ResponseEntity<List<String>> detectIntent(@Valid @RequestBody DetectIntentRequest request) {
        log.info("Received /detect-intent request: {}", request.getQuestion());
        try {
            List<String> docTypes = intentService.detectIntents(request.getQuestion());
            return ResponseEntity.ok(docTypes);
        } catch (Exception e) {
            log.error("Error in /detect-intent: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Endpoint: /api/rag/search-vector
     * Vector search with document type filtering (Step 3-4)
     */
    @PostMapping("/search-vector")
    public ResponseEntity<Map<String, Object>> searchVector(@Valid @RequestBody SearchVectorRequest request) {
        log.info("Received /search-vector request: question={}, docTypes={}", 
                request.getQuestion(), request.getDocTypes());
        try {
            List<RagQueryResponse.SourceDocument> docs = vectorService.searchVectors(
                request.getQuestion(),
                request.getDocTypes(),
                request.getTable(),
                request.getKeyspace(),
                null,  // clusterName - can be added to SearchVectorRequest if needed
                request.getTopK(),
                request.getDaysBack()  // ✅ Production-grade: daysBack from request
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", docs);
            response.put("count", docs.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in /search-vector: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@Valid @RequestBody RagQueryRequest request) {
        log.info("Received RAG query: {}", request.getQuestion());
        try {
            RagQueryResponse response = ragService.query(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing query: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@Valid @RequestBody RagSearchRequest request) {
        log.info("Received search request: {}", request.getQuestion());
        try {
            List<RagQueryResponse.SourceDocument> matches = ragService.search(request);
            Map<String, Object> response = new HashMap<>();
            response.put("matches", matches);
            response.put("count", matches.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in search: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@Valid @RequestBody RagIngestRequest request) {
        log.info("Received ingest request: {} - {}", request.getSourceType(), request.getSourceName());
        try {
            RagDocument document = ragService.ingest(request);
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("status", "success");
            response.put("message", "Document ingested successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error ingesting document: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("spring", "UP");
        
        // Check Phi-4
        try {
            boolean phi4Healthy = ragService.checkPhi4Health();
            health.put("phi4", phi4Healthy ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("phi4", "DOWN");
        }
        
        // Check Yugabyte
        try {
            boolean yugabyteHealthy = ragService.checkYugabyteHealth();
            health.put("yugabyte", yugabyteHealthy ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("yugabyte", "DOWN");
        }
        
        health.put("vector_index", "READY");
        
        return ResponseEntity.ok(health);
    }
}

