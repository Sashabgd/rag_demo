package com.demo.rag.service;

import com.demo.rag.dto.EmbeddingConfig;
import com.demo.rag.dto.EmbeddingModelRequest;
import com.demo.rag.entity.EmbeddingModel;
import com.demo.rag.repository.EmbeddingModelRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmbeddingModelService {

    private final EmbeddingModelRepository repository;

    @Transactional(readOnly = true)
    public List<EmbeddingModel> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public EmbeddingModel getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Embedding model not found: " + id));
    }

    /** The active remote model, or the local e5 model when none is active. */
    @Transactional(readOnly = true)
    public EmbeddingConfig resolveActive() {
        return repository.findFirstByActiveTrue()
                .map(EmbeddingConfig::from)
                .orElseGet(EmbeddingConfig::local);
    }

    @Transactional
    public EmbeddingModel create(EmbeddingModelRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new IllegalArgumentException("An embedding model named '" + name + "' already exists");
        }
        EmbeddingModel model = new EmbeddingModel();
        model.setName(name);
        model.setBaseUrl(request.baseUrl().trim());
        model.setModelName(request.modelName().trim());
        model.setApiKey(normalizeApiKey(request.apiKey()));
        Instant now = Instant.now();
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return repository.save(model);
    }

    @Transactional
    public EmbeddingModel update(Long id, EmbeddingModelRequest request) {
        EmbeddingModel model = getById(id);
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("An embedding model named '" + name + "' already exists");
        }
        model.setName(name);
        model.setBaseUrl(request.baseUrl().trim());
        model.setModelName(request.modelName().trim());
        // Only overwrite the key when a new non-blank value is supplied, so edits don't wipe it.
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            model.setApiKey(request.apiKey().trim());
        }
        model.setUpdatedAt(Instant.now());
        return repository.save(model);
    }

    /** Makes {@code id} the only active model; null deactivates all, falling back to the local model. */
    @Transactional
    public void setActive(Long id) {
        if (id != null) {
            getById(id);
        }
        List<EmbeddingModel> models = repository.findAll();
        for (EmbeddingModel model : models) {
            model.setActive(model.getId().equals(id));
        }
        repository.saveAll(models);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Embedding model not found: " + id);
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
