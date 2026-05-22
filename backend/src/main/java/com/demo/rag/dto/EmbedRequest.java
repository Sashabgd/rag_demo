package com.demo.rag.dto;

import java.util.List;
import java.util.Map;

public record EmbedRequest(List<EmbedDocumentItem> documents) {

    public record EmbedDocumentItem(
            Long chunkId,
            String content,
            Map<String, Object> metadata
    ) {}
}
