package com.demo.rag.service;

import com.demo.rag.dto.ChunkDto;
import com.demo.rag.dto.DocumentDetailDto;
import com.demo.rag.dto.DocumentSummaryDto;
import com.demo.rag.entity.Document;
import com.demo.rag.entity.DocumentChunk;
import com.demo.rag.repository.DocumentChunkRepository;
import com.demo.rag.repository.DocumentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentQueryService {

    private static final int PREVIEW_LEN = 800;
    private static final int CHUNK_PREVIEW_LEN = 200;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public List<DocumentSummaryDto> listAll() {
        return documentRepository.findAll().stream()
                .map(doc -> new DocumentSummaryDto(
                        doc.getId(),
                        doc.getName(),
                        doc.getFileType(),
                        doc.getFileSize(),
                        doc.getStatus(),
                        doc.getUploadedAt(),
                        chunkRepository.findByDocumentIdOrderByStartIndexAsc(doc.getId()).size()
                ))
                .toList();
    }

    public DocumentDetailDto getDetail(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByStartIndexAsc(id);
        String fullText = doc.getFullText();
        String textPreview = fullText == null ? null : truncate(fullText, PREVIEW_LEN);
        Integer textLength = fullText == null ? null : fullText.length();

        List<ChunkDto> chunkDtos = chunks.stream()
                .map(c -> new ChunkDto(
                        c.getId(),
                        c.getStartIndex(),
                        c.getEndIndex(),
                        truncate(c.getContent(), CHUNK_PREVIEW_LEN),
                        c.getVectorStoreId(),
                        c.getStatus()
                ))
                .toList();

        return new DocumentDetailDto(
                doc.getId(),
                doc.getName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getUploadedAt(),
                doc.getParsedAt(),
                doc.getChunkedAt(),
                doc.getEmbeddedAt(),
                textLength,
                textPreview,
                chunkDtos
        );
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
