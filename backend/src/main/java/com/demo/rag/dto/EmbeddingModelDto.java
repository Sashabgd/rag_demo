package com.demo.rag.dto;

import com.demo.rag.entity.EmbeddingModel;
import java.time.Instant;

public record EmbeddingModelDto(
        Long id,
        String name,
        String baseUrl,
        String modelName,
        boolean hasApiKey,
        boolean active,
        String indexName,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmbeddingModelDto from(EmbeddingModel model) {
        return new EmbeddingModelDto(
                model.getId(),
                model.getName(),
                model.getBaseUrl(),
                model.getModelName(),
                model.getApiKey() != null && !model.getApiKey().isBlank(),
                model.isActive(),
                EmbeddingConfig.indexNameFor(model.getModelName()),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
