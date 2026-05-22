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
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable completeOnce = () -> {
            if (finished.compareAndSet(false, true)) {
                emitter.complete();
            }
        };

        Thread.startVirtualThread(() -> {
            try {
                geminiService.chat(request.message(), event -> {
                    if (finished.get()) {
                        return;
                    }
                    try {
                        sendEvent(emitter, event);
                        if ("done".equals(event.type()) || "error".equals(event.type())) {
                            completeOnce.run();
                        }
                    } catch (IOException e) {
                        log.warn("SSE send failed", e);
                        emitter.completeWithError(e);
                        finished.set(true);
                    }
                });
                if (!finished.get()) {
                    completeOnce.run();
                }
            } catch (Exception e) {
                log.error("Chat stream failed", e);
                try {
                    if (!finished.get()) {
                        sendEvent(emitter, ChatEvent.error(
                                e.getMessage() != null ? e.getMessage() : "Chat failed"));
                        completeOnce.run();
                    }
                } catch (IOException io) {
                    emitter.completeWithError(io);
                }
            }
        });

        emitter.onTimeout(completeOnce::run);
        emitter.onError(ex -> completeOnce.run());
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, ChatEvent event) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("type", event.type());
        payload.put("data", event.data());
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name(event.type()).data(json));
    }
}
