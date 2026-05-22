package com.demo.rag.dto;

public record SearchResultItem(
        String content,
        String source,
        Long documentId,
        Long chunkId,
        Integer startIndex,
        Integer endIndex,
        double score
) {}
