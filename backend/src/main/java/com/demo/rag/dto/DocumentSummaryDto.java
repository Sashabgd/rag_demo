package com.demo.rag.dto;

import com.demo.rag.entity.DocumentStatus;
import java.time.Instant;

public record DocumentSummaryDto(
        Long id,
        String name,
        String fileType,
        Long fileSize,
        DocumentStatus status,
        Instant uploadedAt,
        int chunkCount
) {}
