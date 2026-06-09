package com.demo.rag.service;

import com.demo.rag.dto.SearchRequest;
import com.demo.rag.dto.SearchResponse;
import com.demo.rag.dto.SearchResultItem;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared RAG document search executed as a model tool call. Used by every chat
 * provider so the {@code search_documents} tool behaves identically.
 */
@Service
@RequiredArgsConstructor
public class DocumentSearchTool {

    public static final String TOOL_NAME = "search_documents";
    public static final String TOOL_DESCRIPTION =
            "Search uploaded documents semantically. Use when the user asks about document content.";

    private static final int DEFAULT_TOP_K = 5;

    private final EmbeddingClient embeddingClient;

    public static String normalizeRerankType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LOCAL";
        }
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "NONE", "LOCAL", "COHERE" -> upper;
            default -> "LOCAL";
        };
    }

    public static String extractQuery(Map<String, Object> args) {
        if (args == null) {
            return "";
        }
        Object query = args.get("query");
        if (query == null) {
            query = args.get("prompt_query");
        }
        return query != null ? String.valueOf(query) : "";
    }

    public ImmutableMap<String, Object> search(String query, String rerankType) {
        SearchResponse searchResponse = embeddingClient.search(
                new SearchRequest(query, DEFAULT_TOP_K, rerankType));
        List<Map<String, Object>> items = searchResponse.results().stream()
                .map(DocumentSearchTool::toToolResultItem)
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("data", items);
        result.put("rerank_type", searchResponse.rerankType() != null ? searchResponse.rerankType() : "NONE");
        result.put("count", items.size());
        return ImmutableMap.copyOf(result);
    }

    private static Map<String, Object> toToolResultItem(SearchResultItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", item.content() != null ? item.content() : "");
        map.put("source", item.source() != null ? item.source() : "");
        map.put("document_id", item.documentId() != null ? item.documentId() : 0);
        map.put("chunk_id", item.chunkId() != null ? item.chunkId() : 0);
        map.put("score", item.score());
        map.put("start_index", item.startIndex() != null ? item.startIndex() : 0);
        map.put("end_index", item.endIndex() != null ? item.endIndex() : 0);
        return map;
    }
}
