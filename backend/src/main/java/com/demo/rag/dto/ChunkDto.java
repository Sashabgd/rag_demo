package com.demo.rag.dto;

import com.demo.rag.entity.ChunkStatus;

public record ChunkDto(
        Long id,
        Integer startIndex,
        Integer endIndex,
        String contentPreview,
        String vectorStoreId,
        ChunkStatus status
) {}
