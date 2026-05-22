package com.demo.rag.controller;

import com.demo.rag.dto.ChatRequest;
import com.demo.rag.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        return Flux.<ServerSentEvent<String>>create(sink -> {
            geminiService.chat(request.message(), event -> {
                try {
                    String payload = objectMapper.writeValueAsString(
                            java.util.Map.of("type", event.type(), "data", event.data()));
                    sink.next(ServerSentEvent.<String>builder()
                            .event(event.type())
                            .data(payload)
                            .build());
                    if ("done".equals(event.type()) || "error".equals(event.type())) {
                        sink.complete();
                    }
                } catch (Exception e) {
                    sink.error(e);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER)
        .timeout(Duration.ofMinutes(5));
    }
}
