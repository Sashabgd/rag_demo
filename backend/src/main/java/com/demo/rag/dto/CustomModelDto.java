package com.demo.rag.dto;

import com.demo.rag.entity.CustomModel;
import java.time.Instant;

public record CustomModelDto(
        Long id,
        String name,
        String baseUrl,
        String modelName,
        boolean hasApiKey,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomModelDto from(CustomModel model) {
        return new CustomModelDto(
                model.getId(),
                model.getName(),
                model.getBaseUrl(),
                model.getModelName(),
                model.getApiKey() != null && !model.getApiKey().isBlank(),
                model.isEnabled(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
