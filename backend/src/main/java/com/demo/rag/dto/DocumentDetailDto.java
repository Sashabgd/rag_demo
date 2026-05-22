package com.demo.rag.dto;

import com.demo.rag.entity.DocumentStatus;
import java.time.Instant;
import java.util.List;

public record DocumentDetailDto(
        Long id,
        String name,
        String fileType,
        Long fileSize,
        DocumentStatus status,
        Instant uploadedAt,
        Instant parsedAt,
        Instant chunkedAt,
        Instant embeddedAt,
        Integer textLength,
        String textPreview,
        List<ChunkDto> chunks
) {}
