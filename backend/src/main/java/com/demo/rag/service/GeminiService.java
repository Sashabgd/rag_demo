package com.demo.rag.service;

import com.demo.rag.config.RagProperties;
import com.demo.rag.dto.SearchRequest;
import com.demo.rag.dto.SearchResponse;
import com.demo.rag.dto.SearchResultItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private static final String TOOL_NAME = "search_documents";

    private final RagProperties ragProperties;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public void chat(String userMessage, Consumer<ChatEvent> eventConsumer) {
        String apiKey = ragProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            eventConsumer.accept(ChatEvent.error("GEMINI_API_KEY is not configured"));
            return;
        }

        Client client = Client.builder().apiKey(apiKey).build();
        String model = ragProperties.getGemini().getModel();

        String systemPrompt = """
                You are a helpful assistant with access to uploaded documents via the search_documents tool.
                When the user asks about document content, always call search_documents first with a relevant query.
                Answer in the same language as the user. Cite information from retrieved chunks.
                """;

        Content userContent = Content.builder()
                .role("user")
                .parts(Part.builder().text(systemPrompt + "\n\nUser question: " + userMessage).build())
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(buildSearchTool())
                .build();

        try {
            streamWithTools(client, model, List.of(userContent), config, eventConsumer);
        } catch (Exception e) {
            log.error("Gemini chat failed", e);
            eventConsumer.accept(ChatEvent.error(e.getMessage()));
        }
    }

    private void streamWithTools(
            Client client,
            String model,
            List<Content> contents,
            GenerateContentConfig config,
            Consumer<ChatEvent> eventConsumer) throws Exception {

        try (ResponseStream<GenerateContentResponse> stream =
                     client.models.generateContentStream(model, contents, config)) {

            for (GenerateContentResponse response : stream) {
                if (Objects.nonNull(response.functionCalls()) && !response.functionCalls().isEmpty()) {
                    FunctionCall functionCall = response.functionCalls().getFirst();
                    String fnName = functionCall.name().orElse("unknown");
                    Map<String, Object> args = functionCall.args().orElse(Map.of());
                    String query = String.valueOf(args.getOrDefault("query", args.getOrDefault("prompt_query", "")));

                    eventConsumer.accept(ChatEvent.toolCall(fnName, query));

                    ImmutableMap<String, Object> toolResult = executeSearch(query);
                    eventConsumer.accept(ChatEvent.toolResult(toolResult));

                    Content functionCallContent = response.candidates()
                            .flatMap(c -> c.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(c.getFirst()))
                            .flatMap(c -> c.content())
                            .orElse(Content.builder().role("model").build());

                    Content functionResponseContent = Content.builder()
                            .role("function")
                            .parts(Part.builder()
                                    .functionResponse(FunctionResponse.builder()
                                            .name(functionCall.name().get())
                                            .response(toolResult)
                                            .build())
                                    .build())
                            .build();

                    List<Content> fullHistory = new ArrayList<>(contents);
                    fullHistory.add(functionCallContent);
                    fullHistory.add(functionResponseContent);
                    streamWithTools(client, model, fullHistory, config, eventConsumer);
                    return;
                }

                String text = response.text();
                if (text != null && !text.isBlank()) {
                    eventConsumer.accept(ChatEvent.token(text));
                }
            }
            eventConsumer.accept(ChatEvent.done());
        }
    }

    private ImmutableMap<String, Object> executeSearch(String query) {
        SearchResponse searchResponse = embeddingClient.search(new SearchRequest(query, 5, true));
        List<Map<String, Object>> items = searchResponse.results().stream()
                .map(this::toToolResultItem)
                .toList();
        return ImmutableMap.of(
                "data", items,
                "rerank_type", searchResponse.rerankType() != null ? searchResponse.rerankType() : "NONE",
                "count", items.size()
        );
    }

    private Map<String, Object> toToolResultItem(SearchResultItem item) {
        return Map.of(
                "content", item.content() != null ? item.content() : "",
                "source", item.source() != null ? item.source() : "",
                "document_id", item.documentId() != null ? item.documentId() : 0,
                "chunk_id", item.chunkId() != null ? item.chunkId() : 0,
                "score", item.score(),
                "start_index", item.startIndex() != null ? item.startIndex() : 0,
                "end_index", item.endIndex() != null ? item.endIndex() : 0
        );
    }

    private Tool buildSearchTool() {
        ImmutableMap<String, Object> parametersSchema = ImmutableMap.of(
                "type", "object",
                "properties", ImmutableMap.of(
                        "query", ImmutableMap.of(
                                "type", "string",
                                "description", "Search query to find relevant document passages"
                        )
                ),
                "required", ImmutableList.of("query")
        );

        return Tool.builder()
                .functionDeclarations(FunctionDeclaration.builder()
                        .name(TOOL_NAME)
                        .description("Search uploaded documents semantically. Use when the user asks about document content.")
                        .parametersJsonSchema(parametersSchema)
                        .build())
                .build();
    }

    public record ChatEvent(String type, String data) {
        public static ChatEvent token(String text) {
            return new ChatEvent("token", text);
        }

        public static ChatEvent toolCall(String name, String query) {
            try {
                return new ChatEvent("tool_call", new ObjectMapper().writeValueAsString(
                        Map.of("name", name, "query", query)));
            } catch (Exception e) {
                return new ChatEvent("tool_call", "{\"name\":\"" + name + "\",\"query\":\"" + query + "\"}");
            }
        }

        public static ChatEvent toolResult(ImmutableMap<String, Object> result) {
            try {
                return new ChatEvent("tool_result", new ObjectMapper().writeValueAsString(result));
            } catch (Exception e) {
                return new ChatEvent("tool_result", "{}");
            }
        }

        public static ChatEvent done() {
            return new ChatEvent("done", "");
        }

        public static ChatEvent error(String message) {
            return new ChatEvent("error", message);
        }
    }
}
