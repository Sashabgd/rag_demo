package com.demo.rag.dto;

import com.demo.rag.entity.EmbeddingModel;
import java.util.Locale;

/**
 * Embedding model + Redis index sent to the embedding service with every embed/search request.
 * A null {@code baseUrl} means the service's local e5 model. {@code label} is for display only.
 */
public record EmbeddingConfig(
        String indexName,
        String label,
        String baseUrl,
        String modelName,
        String apiKey
) {
    public static final String LOCAL_INDEX = "rag_demo";
    public static final String LOCAL_LABEL = "e5-small (lokalni)";

    public static EmbeddingConfig local() {
        return new EmbeddingConfig(LOCAL_INDEX, LOCAL_LABEL, null, null, null);
    }

    public static EmbeddingConfig from(EmbeddingModel model) {
        return new EmbeddingConfig(
                indexNameFor(model.getModelName()),
                model.getName(),
                model.getBaseUrl(),
                model.getModelName(),
                model.getApiKey()
        );
    }

    /** Each remote model gets its own index, since vector dimensions differ between models. */
    public static String indexNameFor(String modelName) {
        String slug = modelName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return LOCAL_INDEX + "_" + (slug.isEmpty() ? "remote" : slug);
    }
}
