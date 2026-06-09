package com.demo.rag.service;

import com.demo.rag.config.RagProperties;
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

    private static final int MAX_TOOL_CALLS = 5;

    private final RagProperties ragProperties;
    private final DocumentSearchTool searchTool;

    public void chat(String userMessage, String rerankType, Consumer<ChatEvent> eventConsumer) {
        String apiKey = ragProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            eventConsumer.accept(ChatEvent.error("GEMINI_API_KEY is not configured"));
            eventConsumer.accept(ChatEvent.done());
            return;
        }

        Client client = Client.builder().apiKey(apiKey).build();
        String model = ragProperties.getGemini().getModel();
        String normalizedRerank = DocumentSearchTool.normalizeRerankType(rerankType);

        String systemPrompt = """
                You are a helpful assistant with access to uploaded documents via the search_documents tool.
                When the user asks about document content, always call search_documents first with a relevant query.
                You can call multiple times to get relevant documents
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
            runChatTurn(client, model, new ArrayList<>(List.of(userContent)), config, eventConsumer, 0, normalizedRerank);
        } catch (Exception e) {
            log.error("Gemini chat failed", e);
            eventConsumer.accept(ChatEvent.error(e.getMessage() != null ? e.getMessage() : "Chat failed"));
            eventConsumer.accept(ChatEvent.done());
        }
    }

    /**
     * Stream tokens in real time. Supports multiple tool rounds while tools remain in config.
     */
    private void runChatTurn(
            Client client,
            String model,
            List<Content> contents,
            GenerateContentConfig config,
            Consumer<ChatEvent> eventConsumer,
            int toolCallsSoFar,
            String rerankType) throws Exception {

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
            handleToolCall(client, model, contents, config, eventConsumer, pendingCall, modelTurnContent, toolCallsSoFar, rerankType);
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
            int toolCallsSoFar,
            String rerankType) throws Exception {

        if (toolCallsSoFar >= MAX_TOOL_CALLS) {
            eventConsumer.accept(ChatEvent.error("Too many tool calls"));
            eventConsumer.accept(ChatEvent.done());
            return;
        }

        String fnName = functionCall.name().orElse(DocumentSearchTool.TOOL_NAME);
        Map<String, Object> args = functionCall.args().orElse(Map.of());
        String query = DocumentSearchTool.extractQuery(args);

        eventConsumer.accept(ChatEvent.toolCall(fnName, query));

        ImmutableMap<String, Object> toolResult = searchTool.search(query, rerankType);
        eventConsumer.accept(ChatEvent.toolResult(toolResult));

        if (modelTurnContent != null) {
            contents.add(modelTurnContent);
        } else {
            contents.add(Content.builder()
                    .role("model")
                    .parts(Part.builder()
                            .functionCall(functionCall)
                            .build())
                    .build());
        }

        contents.add(Content.builder()
                .role("user")
                .parts(Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .name(functionCall.name().orElse(DocumentSearchTool.TOOL_NAME))
                                .response(toolResult)
                                .build())
                        .build())
                .build());

        // Keep tools enabled so the model can call search_documents again if needed
        runChatTurn(client, model, contents, config, eventConsumer, toolCallsSoFar + 1, rerankType);
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
                        .name(DocumentSearchTool.TOOL_NAME)
                        .description(DocumentSearchTool.TOOL_DESCRIPTION)
                        .parametersJsonSchema(parametersSchema)
                        .build())
                .build();
    }
}
