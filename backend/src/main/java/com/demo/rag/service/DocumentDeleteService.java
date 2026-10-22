package com.demo.rag.service;

import com.demo.rag.entity.Document;
import com.demo.rag.repository.DocumentRepository;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes documents from Redis first and Postgres second: if the Redis call fails nothing is
 * removed from Postgres, so the delete can simply be retried without leaving searchable vectors behind.
 */
@Service
@RequiredArgsConstructor
public class DocumentDeleteService {

    private final DocumentRepository documentRepository;
    private final EmbeddingClient embeddingClient;

    @Transactional
    public void delete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
        if (doc.getEmbeddingIndex() != null) {
            embeddingClient.deleteDocument(doc.getEmbeddingIndex(), id);
        }
        // document_chunks rows are removed by ON DELETE CASCADE
        documentRepository.delete(doc);
    }

    @Transactional
    public void deleteAll() {
        embeddingClient.deleteAll();
        documentRepository.deleteAllInBatch();
    }
}
