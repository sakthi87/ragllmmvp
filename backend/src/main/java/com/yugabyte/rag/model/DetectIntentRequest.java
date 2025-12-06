package com.yugabyte.rag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request model for /api/rag/detect-intent endpoint.
 */
@Data
public class DetectIntentRequest {
    @NotBlank(message = "Question is required")
    private String question;
}

