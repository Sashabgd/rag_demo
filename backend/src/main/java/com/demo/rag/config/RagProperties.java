package com.demo.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
@Getter
@Setter
public class RagProperties {

    private String embeddingServiceUrl = "http://localhost:8002";
    private Gemini gemini = new Gemini();

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-2.5-flash";
    }
}
