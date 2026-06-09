package com.demo.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Streaming chat event emitted to the SSE layer. Shared by all chat providers
 * (Gemini, OpenAI-compatible) so the controller and frontend handle them uniformly.
 */
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

    public static ChatEvent toolResult(Map<String, Object> result) {
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
