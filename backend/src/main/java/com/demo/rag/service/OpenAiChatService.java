package com.demo.rag.service;

import com.demo.rag.entity.CustomModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Chat provider for OpenAI-compatible servers (e.g. vLLM). Streams completions over SSE
 * and runs the same {@code search_documents} RAG tool loop as {@link GeminiService},
 * emitting identical {@link ChatEvent}s so the SSE controller and frontend are provider-agnostic.
 */
@Service
@Slf4j
public class OpenAiChatService {

    private static final int MAX_TOOL_CALLS = 5;
    private static final String DONE_SENTINEL = "[DONE]";

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant with access to uploaded documents via the search_documents tool.
            When the user asks about document content, always call search_documents first with a relevant query.
            You can call multiple times to get relevant documents.
            Answer in the same language as the user. Cite information from retrieved chunks.
            """;

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final DocumentSearchTool searchTool;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiChatService(DocumentSearchTool searchTool, ObjectMapper objectMapper) {
        this.searchTool = searchTool;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public void chat(String userMessage, String rerankType, CustomModel model, Consumer<ChatEvent> eventConsumer) {
        if (model == null) {
            eventConsumer.accept(ChatEvent.error("Custom model not found"));
            eventConsumer.accept(ChatEvent.done());
            return;
        }
        if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()
                || model.getModelName() == null || model.getModelName().isBlank()) {
            eventConsumer.accept(ChatEvent.error("Custom model is missing baseUrl or modelName"));
            eventConsumer.accept(ChatEvent.done());
            return;
        }

        String normalizedRerank = DocumentSearchTool.normalizeRerankType(rerankType);
        URI endpoint = resolveEndpoint(model.getBaseUrl());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(textMessage("system", SYSTEM_PROMPT));
        messages.add(textMessage("user", userMessage));

        try {
            int toolTurns = 0;
            while (true) {
                StreamOutcome outcome = streamOnce(endpoint, model, messages, eventConsumer);

                if (outcome.toolCalls().isEmpty()) {
                    eventConsumer.accept(ChatEvent.done());
                    return;
                }

                if (toolTurns >= MAX_TOOL_CALLS) {
                    eventConsumer.accept(ChatEvent.error("Too many tool calls"));
                    eventConsumer.accept(ChatEvent.done());
                    return;
                }
                toolTurns++;

                messages.add(assistantToolCallMessage(outcome.toolCalls()));
                for (ToolCall toolCall : outcome.toolCalls()) {
                    String query = extractQuery(toolCall.arguments());
                    eventConsumer.accept(ChatEvent.toolCall(toolCall.name(), query));
                    ImmutableMap<String, Object> result = searchTool.search(query, normalizedRerank);
                    eventConsumer.accept(ChatEvent.toolResult(result));
                    messages.add(toolResultMessage(toolCall.id(), result));
                }
            }
        } catch (Exception e) {
            log.error("OpenAI chat failed", e);
            eventConsumer.accept(ChatEvent.error(friendlyError(e)));
            eventConsumer.accept(ChatEvent.done());
        }
    }

    /**
     * Performs one streaming request, emitting token events as content arrives and
     * accumulating any streamed tool calls (delta fragments are keyed by their index).
     */
    private StreamOutcome streamOnce(
            URI endpoint,
            CustomModel model,
            List<Map<String, Object>> messages,
            Consumer<ChatEvent> eventConsumer) {

        Map<String, Object> body = buildRequestBody(model.getModelName(), messages);
        Map<Integer, ToolCallBuilder> toolCallsByIndex = new LinkedHashMap<>();
        String apiKey = model.getApiKey();

        try (Stream<ServerSentEvent<String>> stream = webClient.post()
                .uri(endpoint)
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey.trim());
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .toStream()) {

            for (ServerSentEvent<String> event : (Iterable<ServerSentEvent<String>>) stream::iterator) {
                String data = event.data();
                if (data == null) {
                    continue;
                }
                data = data.trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (DONE_SENTINEL.equals(data)) {
                    break;
                }
                consumeChunk(data, toolCallsByIndex, eventConsumer);
            }
        } catch (WebClientResponseException e) {
            throw new RuntimeException(
                    "Model server returned " + e.getStatusCode().value() + ": " + truncate(e.getResponseBodyAsString()),
                    e);
        }

        List<ToolCall> finalized = new ArrayList<>();
        for (ToolCallBuilder builder : toolCallsByIndex.values()) {
            if (builder.name != null && !builder.name.isBlank()) {
                finalized.add(builder.build());
            }
        }
        return new StreamOutcome(finalized);
    }

    private void consumeChunk(
            String data,
            Map<Integer, ToolCallBuilder> toolCallsByIndex,
            Consumer<ChatEvent> eventConsumer) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode delta = choices.get(0).path("delta");

            JsonNode content = delta.path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                eventConsumer.accept(ChatEvent.token(content.asText()));
            }

            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode toolCall : toolCalls) {
                    int index = toolCall.path("index").asInt(0);
                    ToolCallBuilder builder =
                            toolCallsByIndex.computeIfAbsent(index, k -> new ToolCallBuilder());
                    if (toolCall.hasNonNull("id")) {
                        builder.id = toolCall.get("id").asText();
                    }
                    JsonNode function = toolCall.path("function");
                    if (function.hasNonNull("name")) {
                        builder.name = function.get("name").asText();
                    }
                    if (function.hasNonNull("arguments")) {
                        builder.arguments.append(function.get("arguments").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Skipping unparseable stream chunk: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildRequestBody(String modelName, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("tools", List.of(buildToolSpec()));
        body.put("tool_choice", "auto");
        body.put("stream", true);
        return body;
    }

    private Map<String, Object> buildToolSpec() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of(
                "query", Map.of(
                        "type", "string",
                        "description", "Search query to find relevant document passages")));
        parameters.put("required", List.of("query"));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", DocumentSearchTool.TOOL_NAME);
        function.put("description", DocumentSearchTool.TOOL_DESCRIPTION);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private static Map<String, Object> textMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Map<String, Object> assistantToolCallMessage(List<ToolCall> toolCalls) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", toolCall.name());
            function.put("arguments", toolCall.arguments() != null ? toolCall.arguments() : "{}");

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", toolCall.id());
            entry.put("type", "function");
            entry.put("function", function);
            serialized.add(entry);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("tool_calls", serialized);
        return message;
    }

    private Map<String, Object> toolResultMessage(String toolCallId, Map<String, Object> result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        try {
            message.put("content", objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            message.put("content", "{}");
        }
        return message;
    }

    private String extractQuery(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> args =
                    objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            return DocumentSearchTool.extractQuery(args);
        } catch (Exception e) {
            log.debug("Failed to parse tool arguments '{}': {}", argumentsJson, e.getMessage());
            return "";
        }
    }

    private static URI resolveEndpoint(String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/chat/completions");
    }

    private static String friendlyError(Exception e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : "Chat failed";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record ToolCall(String id, String name, String arguments) {}

    private record StreamOutcome(List<ToolCall> toolCalls) {}

    private static final class ToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolCall build() {
            return new ToolCall(id, name, arguments.toString());
        }
    }
}
