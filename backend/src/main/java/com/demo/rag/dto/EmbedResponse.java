package com.demo.rag.dto;

import java.util.List;

public record EmbedResponse(List<EmbedIdMapping> ids) {

    public record EmbedIdMapping(Long chunkId, String vectorStoreId) {}
}
