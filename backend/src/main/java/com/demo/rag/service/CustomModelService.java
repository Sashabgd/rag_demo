package com.demo.rag.service;

import com.demo.rag.dto.CustomModelRequest;
import com.demo.rag.entity.CustomModel;
import com.demo.rag.repository.CustomModelRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomModelService {

    private final CustomModelRepository repository;

    @Transactional(readOnly = true)
    public List<CustomModel> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public CustomModel getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Custom model not found: " + id));
    }

    @Transactional
    public CustomModel create(CustomModelRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new IllegalArgumentException("A model named '" + name + "' already exists");
        }
        CustomModel model = new CustomModel();
        model.setName(name);
        model.setBaseUrl(request.baseUrl().trim());
        model.setModelName(request.modelName().trim());
        model.setApiKey(normalizeApiKey(request.apiKey()));
        model.setEnabled(request.enabled() == null || request.enabled());
        Instant now = Instant.now();
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return repository.save(model);
    }

    @Transactional
    public CustomModel update(Long id, CustomModelRequest request) {
        CustomModel model = getById(id);
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("A model named '" + name + "' already exists");
        }
        model.setName(name);
        model.setBaseUrl(request.baseUrl().trim());
        model.setModelName(request.modelName().trim());
        // Only overwrite the key when a new non-blank value is supplied, so edits don't wipe it.
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            model.setApiKey(request.apiKey().trim());
        }
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        model.setUpdatedAt(Instant.now());
        return repository.save(model);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Custom model not found: " + id);
        }
        repository.deleteById(id);
    }

    private static String normalizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return apiKey.trim();
    }
}
