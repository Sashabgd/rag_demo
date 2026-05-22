package com.demo.rag.repository;

import com.demo.rag.entity.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByStartIndexAsc(Long documentId);
}
