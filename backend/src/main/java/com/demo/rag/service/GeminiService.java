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
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
    private static final int MAX_TOOL_ROUNDS = 2;

    private final RagProperties ragProperties;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public void chat(String userMessage, Consumer<ChatEvent> eventConsumer) {
        String apiKey = ragProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            eventConsumer.accept(ChatEvent.error("GEMINI_API_KEY is not configured"));
            eventConsumer.accept(ChatEvent.done());
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
            runChatTurn(client, model, new ArrayList<>(List.of(userContent)), config, eventConsumer, 0);
        } catch (Exception e) {
            log.error("Gemini chat failed", e);
            eventConsumer.accept(ChatEvent.error(e.getMessage() != null ? e.getMessage() : "Chat failed"));
            eventConsumer.accept(ChatEvent.done());
        }
    }

    /**
     * Stream tokens in real time. First turn may trigger tool; final answer streams without tools.
     */
    private void runChatTurn(
            Client client,
            String model,
            List<Content> contents,
            GenerateContentConfig config,
            Consumer<ChatEvent> eventConsumer,
            int round) throws Exception {

        if (round >= MAX_TOOL_ROUNDS) {
            eventConsumer.accept(ChatEvent.error("Too many tool rounds"));
            eventConsumer.accept(ChatEvent.done());
            return;
        }

        FunctionCall pendingCall = null;
        Content modelTurnContent = null;
        boolean streamedText = false;

        try (ResponseStream<GenerateContentResponse> stream =
                client.models.generateContentStream(model, contents, config)) {

            Iterator<GenerateContentResponse> iterator = stream.iterator();
            while (hasNextSafely(iterator)) {
                GenerateContentResponse response = nextSafely(iterator);
                if (response == null) {
                    break;
                }

                if (Objects.nonNull(response.functionCalls()) && !response.functionCalls().isEmpty()) {
                    pendingCall = response.functionCalls().getFirst();
                    modelTurnContent = response.candidates()
                            .filter(c -> !c.isEmpty())
                            .map(c -> c.getFirst().content().orElse(null))
                            .orElse(null);
                    break;
                }

                String text = response.text();
                if (text != null && !text.isBlank()) {
                    streamedText = true;
                    eventConsumer.accept(ChatEvent.token(text));
                }
            }
        } catch (GenAiIOException e) {
            log.warn("Gemini stream parse warning (often trailing empty chunk): {}", e.getMessage());
        }

        if (pendingCall != null) {
            handleToolCall(client, model, contents, config, eventConsumer, pendingCall, modelTurnContent, round);
            return;
        }

        if (!streamedText) {
            GenerateContentConfig answerOnly = GenerateContentConfig.builder().build();
            streamTokens(client, model, contents, answerOnly, eventConsumer);
            return;
        }

        eventConsumer.accept(ChatEvent.done());
    }

    private void handleToolCall(
            Client client,
            String model,
            List<Content> contents,
            GenerateContentConfig config,
            Consumer<ChatEvent> eventConsumer,
            FunctionCall functionCall,
            Content modelTurnContent,
            int round) throws Exception {

        String fnName = functionCall.name().orElse(TOOL_NAME);
        Map<String, Object> args = functionCall.args().orElse(Map.of());
        String query = extractQuery(args);

        eventConsumer.accept(ChatEvent.toolCall(fnName, query));

        ImmutableMap<String, Object> toolResult = executeSearch(query);
        eventConsumer.accept(ChatEvent.toolResult(toolResult));

        if (modelTurnContent != null) {
            contents.add(modelTurnContent);
        } else {
            contents.add(Content.builder()
                    .role("model")
                    .parts(Part.builder()
                            .text("Calling " + fnName + " with query: " + query)
                            .build())
                    .build());
        }

        contents.add(Content.builder()
                .role("user")
                .parts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .name(functionCall.name().orElse(TOOL_NAME))
                                .response(toolResult)
                                .build())
                        .build())
                .build());

        // Final answer: stream without tools (no second tool round; tokens arrive incrementally)
        GenerateContentConfig answerConfig = GenerateContentConfig.builder().build();
        streamTokens(client, model, contents, answerConfig, eventConsumer);
    }

    private void streamTokens(
            Client client,
            String model,
            List<Content> contents,
            GenerateContentConfig config,
            Consumer<ChatEvent> eventConsumer) {

        boolean anyToken = false;

        try (ResponseStream<GenerateContentResponse> stream =
                client.models.generateContentStream(model, contents, config)) {

            Iterator<GenerateContentResponse> iterator = stream.iterator();
            while (hasNextSafely(iterator)) {
                GenerateContentResponse response = nextSafely(iterator);
                if (response == null) {
                    break;
                }
                if (Objects.nonNull(response.functionCalls()) && !response.functionCalls().isEmpty()) {
                    log.debug("Unexpected tool call in answer stream, stopping");
                    break;
                }
                String text = response.text();
                if (text != null && !text.isBlank()) {
                    anyToken = true;
                    eventConsumer.accept(ChatEvent.token(text));
                }
            }
        } catch (GenAiIOException e) {
            log.warn("Gemini answer stream warning: {}", e.getMessage());
        }

        if (!anyToken) {
            GenerateContentResponse response = client.models.generateContent(model, contents, config);
            String text = response.text();
            if (text != null && !text.isBlank()) {
                eventConsumer.accept(ChatEvent.token(text));
            }
        }

        eventConsumer.accept(ChatEvent.done());
    }

    private static String extractQuery(Map<String, Object> args) {
        Object query = args.get("query");
        if (query == null) {
            query = args.get("prompt_query");
        }
        return query != null ? String.valueOf(query) : "";
    }

    private static boolean hasNextSafely(Iterator<GenerateContentResponse> iterator) {
        try {
            return iterator.hasNext();
        } catch (GenAiIOException e) {
            log.warn("Gemini stream hasNext failed: {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof GenAiIOException) {
                log.warn("Gemini stream hasNext failed: {}", e.getCause().getMessage());
                return false;
            }
            throw e;
        }
    }

    private static GenerateContentResponse nextSafely(Iterator<GenerateContentResponse> iterator) {
        try {
            return iterator.next();
        } catch (GenAiIOException e) {
            log.warn("Gemini stream next failed: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof GenAiIOException) {
                log.warn("Gemini stream next failed: {}", e.getCause().getMessage());
                return null;
            }
            throw e;
        }
    }

    private ImmutableMap<String, Object> executeSearch(String query) {
        SearchResponse searchResponse = embeddingClient.search(new SearchRequest(query, 5, true));
        List<Map<String, Object>> items = searchResponse.results().stream()
                .map(this::toToolResultItem)
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("data", items);
        result.put("rerank_type", searchResponse.rerankType() != null ? searchResponse.rerankType() : "NONE");
        result.put("count", items.size());
        return ImmutableMap.copyOf(result);
    }

    private Map<String, Object> toToolResultItem(SearchResultItem item) {
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
