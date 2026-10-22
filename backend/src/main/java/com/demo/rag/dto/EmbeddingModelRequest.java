package com.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record EmbeddingModelRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "baseUrl is required") String baseUrl,
        @NotBlank(message = "modelName is required") String modelName,
        String apiKey
) {}
