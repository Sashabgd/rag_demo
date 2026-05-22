package com.demo.rag.service;

import com.demo.rag.config.RagProperties;
import com.demo.rag.dto.EmbedRequest;
import com.demo.rag.dto.EmbedResponse;
import com.demo.rag.dto.SearchRequest;
import com.demo.rag.dto.SearchResponse;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingClient {

    private final RestClient restClient;

    public EmbeddingClient(RagProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofMinutes(10));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getEmbeddingServiceUrl())
                .requestFactory(factory)
                .build();
    }

    public EmbedResponse embed(EmbedRequest request) {
        return restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbedResponse.class);
    }

    public SearchResponse search(SearchRequest request) {
        return restClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SearchResponse.class);
    }
}
