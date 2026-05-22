package com.demo.rag.controller;

import com.demo.rag.dto.ChatRequest;
import com.demo.rag.service.GeminiService;
import com.demo.rag.service.GeminiService.ChatEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable closeQuietly = () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.complete();
            } catch (Exception e) {
                if (!isClientDisconnected(e)) {
                    log.debug("SSE complete skipped: {}", e.getMessage());
                }
            }
        };

        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(closeQuietly);
        emitter.onError(ex -> closed.set(true));

        Thread.startVirtualThread(() -> {
            try {
                geminiService.chat(request.message(), event -> {
                    if (closed.get()) {
                        return;
                    }
                    if (!sendEventSafe(emitter, event, closed)) {
                        return;
                    }
                    if ("done".equals(event.type()) || "error".equals(event.type())) {
                        closeQuietly.run();
                    }
                });
                if (!closed.get()) {
                    closeQuietly.run();
                }
            } catch (Exception e) {
                if (isClientDisconnected(e)) {
                    log.debug("Chat stopped: client disconnected");
                    closed.set(true);
                    return;
                }
                log.error("Chat stream failed", e);
                if (!closed.get()) {
                    sendEventSafe(emitter, ChatEvent.error(
                            e.getMessage() != null ? e.getMessage() : "Chat failed"), closed);
                    closeQuietly.run();
                }
            }
        });

        return emitter;
    }

    private boolean sendEventSafe(SseEmitter emitter, ChatEvent event, AtomicBoolean closed) {
        if (closed.get()) {
            return false;
        }
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("type", event.type());
            payload.put("data", event.data());
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(event.type()).data(json));
            return true;
        } catch (IOException | IllegalStateException e) {
            if (isClientDisconnected(e)) {
                log.debug("SSE client disconnected during send ({})", event.type());
                closed.set(true);
                return false;
            }
            log.warn("SSE send failed for event {}: {}", event.type(), e.getMessage());
            closed.set(true);
            return false;
        }
    }

    private static boolean isClientDisconnected(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ClientAbortException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("connection aborted")
                        || lower.contains("asyncrequestnotusable")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
