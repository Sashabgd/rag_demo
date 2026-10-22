package com.demo.rag.dto;

/** {@code id} of the embedding model to activate; null switches back to the local e5 model. */
public record ActiveEmbeddingModelRequest(Long id) {}
