package com.demo.rag.controller;

import com.demo.rag.dto.ActiveEmbeddingModelRequest;
import com.demo.rag.dto.EmbeddingModelDto;
import com.demo.rag.dto.EmbeddingModelRequest;
import com.demo.rag.service.EmbeddingModelService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/embedding-models")
@RequiredArgsConstructor
public class EmbeddingModelController {

    private final EmbeddingModelService service;

    @GetMapping
    public List<EmbeddingModelDto> list() {
        return service.listAll().stream().map(EmbeddingModelDto::from).toList();
    }

    @GetMapping("/{id}")
    public EmbeddingModelDto get(@PathVariable Long id) {
        return EmbeddingModelDto.from(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<EmbeddingModelDto> create(@Valid @RequestBody EmbeddingModelRequest request) {
        return ResponseEntity.ok(EmbeddingModelDto.from(service.create(request)));
    }

    @PutMapping("/{id}")
    public EmbeddingModelDto update(@PathVariable Long id, @Valid @RequestBody EmbeddingModelRequest request) {
        return EmbeddingModelDto.from(service.update(id, request));
    }

    @PutMapping("/active")
    public ResponseEntity<Void> setActive(@RequestBody ActiveEmbeddingModelRequest request) {
        service.setActive(request.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
