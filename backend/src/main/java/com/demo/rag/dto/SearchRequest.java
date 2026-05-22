package com.demo.rag.dto;

public record SearchRequest(String query, int topK, boolean rerank) {}
