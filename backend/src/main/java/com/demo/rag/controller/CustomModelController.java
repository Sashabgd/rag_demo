package com.demo.rag.controller;

import com.demo.rag.dto.CustomModelDto;
import com.demo.rag.dto.CustomModelRequest;
import com.demo.rag.service.CustomModelService;
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
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class CustomModelController {

    private final CustomModelService service;

    @GetMapping
    public List<CustomModelDto> list() {
        return service.listAll().stream().map(CustomModelDto::from).toList();
    }

    @GetMapping("/{id}")
    public CustomModelDto get(@PathVariable Long id) {
        return CustomModelDto.from(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<CustomModelDto> create(@Valid @RequestBody CustomModelRequest request) {
        return ResponseEntity.ok(CustomModelDto.from(service.create(request)));
    }

    @PutMapping("/{id}")
    public CustomModelDto update(@PathVariable Long id, @Valid @RequestBody CustomModelRequest request) {
        return CustomModelDto.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
