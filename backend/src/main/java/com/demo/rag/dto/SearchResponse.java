package com.demo.rag.dto;

import java.util.List;

public record SearchResponse(
        List<SearchResultItem> results,
        String rerankType,
        int billedDocuments
) {}
