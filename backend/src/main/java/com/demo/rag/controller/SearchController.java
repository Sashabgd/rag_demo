package com.demo.rag.controller;

import com.demo.rag.dto.SearchRequest;
import com.demo.rag.dto.SearchResponse;
import com.demo.rag.service.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final EmbeddingClient embeddingClient;

    @PostMapping
    public SearchResponse search(@RequestBody SearchRequest request) {
        int topK = request.topK() > 0 ? request.topK() : 5;
        String rerankType = normalizeRerankType(request.rerankType());
        return embeddingClient.search(new SearchRequest(request.query(), topK, rerankType));
    }

    private static String normalizeRerankType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LOCAL";
        }
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "NONE", "LOCAL", "COHERE" -> upper;
            default -> "LOCAL";
        };
    }
}
