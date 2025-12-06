package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchRequest {
    
    @NotBlank(message = "Question is required")
    private String question;
    
    private String table;
    
    private Integer topK;
}

