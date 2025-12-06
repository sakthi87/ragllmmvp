package com.yugabyte.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PromptEngine {
    
    public String detectMode(String question) {
        String lowerQuestion = question.toLowerCase();
        
        if (lowerQuestion.contains("why") || lowerQuestion.contains("root cause") || 
            lowerQuestion.contains("reason") || lowerQuestion.contains("caused")) {
            return "RCA";
        }
        
        if (lowerQuestion.contains("schema") || lowerQuestion.contains("ttl") || 
            lowerQuestion.contains("what is") || lowerQuestion.contains("primary key") ||
            lowerQuestion.contains("owner") || lowerQuestion.contains("pii")) {
            return "METADATA";
        }
        
        if (lowerQuestion.contains("which loads") || lowerQuestion.contains("feeds") || 
            lowerQuestion.contains("pipeline") || lowerQuestion.contains("kafka topic") ||
            lowerQuestion.contains("spark job") || lowerQuestion.contains("which api")) {
            return "LINEAGE";
        }
        
        if (lowerQuestion.contains("latency") || lowerQuestion.contains("lag") || 
            lowerQuestion.contains("throughput") || lowerQuestion.contains("performance") ||
            lowerQuestion.contains("metric")) {
            return "METRICS";
        }
        
        if (lowerQuestion.contains("failed") || lowerQuestion.contains("error") || 
            lowerQuestion.contains("crash") || lowerQuestion.contains("log")) {
            return "LOGS";
        }
        
        return "GENERAL";
    }
    
    public String buildPrompt(String question, String context, String mode) {
        StringBuilder prompt = new StringBuilder();
        
        // Mode-specific instructions
        switch (mode) {
            case "RCA":
                prompt.append("You are a data platform expert analyzing root causes. ");
                prompt.append("Based on the following context from logs, metrics, and lineage, ");
                prompt.append("provide a clear root cause analysis.\n\n");
                break;
            case "METADATA":
                prompt.append("You are a data catalog expert. ");
                prompt.append("Answer questions about table schemas, TTL, ownership, and metadata.\n\n");
                break;
            case "LINEAGE":
                prompt.append("You are a data lineage expert. ");
                prompt.append("Explain data flow, pipelines, and system dependencies.\n\n");
                break;
            case "METRICS":
                prompt.append("You are a performance analyst. ");
                prompt.append("Analyze metrics, latency, throughput, and performance data.\n\n");
                break;
            case "LOGS":
                prompt.append("You are a system analyst. ");
                prompt.append("Analyze logs, errors, failures, and system events.\n\n");
                break;
            default:
                prompt.append("You are a helpful assistant. ");
                prompt.append("Answer questions based on the provided context.\n\n");
        }
        
        // Context
        prompt.append("Context:\n");
        prompt.append(context);
        prompt.append("\n\n");
        
        // Question
        prompt.append("Question: ");
        prompt.append(question);
        prompt.append("\n\n");
        
        // Instructions
        prompt.append("Instructions:\n");
        prompt.append("- Answer based ONLY on the context provided\n");
        prompt.append("- If the context doesn't contain the answer, say so\n");
        prompt.append("- Be specific and cite relevant details from the context\n");
        prompt.append("- Format your answer clearly\n");
        prompt.append("\nAnswer:");
        
        return prompt.toString();
    }
}

