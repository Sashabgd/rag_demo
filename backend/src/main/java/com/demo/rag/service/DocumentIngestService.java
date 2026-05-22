package com.demo.rag.service;

import com.demo.rag.chunk.StaticSplitter;
import com.demo.rag.chunk.TextChunk;
import com.demo.rag.dto.EmbedRequest;
import com.demo.rag.dto.EmbedResponse;
import com.demo.rag.entity.ChunkStatus;
import com.demo.rag.entity.Document;
import com.demo.rag.entity.DocumentChunk;
import com.demo.rag.entity.DocumentStatus;
import com.demo.rag.repository.DocumentChunkRepository;
import com.demo.rag.repository.DocumentRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestService {

    private static final int PAGE_SIZE = 1200;
    private static final int OVERLAP = 200;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final EmbeddingClient embeddingClient;

    @Async("ingestExecutor")
    public void processDocumentAsync(Long documentId, byte[] fileBytes, String extension) {
        try {
            processDocument(documentId, fileBytes, extension);
        } catch (Exception e) {
            log.error("Ingest failed for document {}", documentId, e);
            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(DocumentStatus.FAILED);
                documentRepository.save(doc);
            });
        }
    }

    @Transactional
    public void processDocument(Long documentId, byte[] fileBytes, String extension) throws Exception {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        // Step 2: Parse
        String text = documentParser.parse(fileBytes, extension);
        doc.setFullText(text);
        doc.setStatus(DocumentStatus.PARSED);
        doc.setParsedAt(Instant.now());
        documentRepository.saveAndFlush(doc);
        log.info("Document {} parsed, {} chars", documentId, text.length());

        // Step 3: Chunk (already saved text in step above)
        StaticSplitter splitter = new StaticSplitter(PAGE_SIZE, OVERLAP);
        List<TextChunk> chunks = splitter.process(text);
        List<DocumentChunk> chunkEntities = chunks.stream().map(c -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setContent(c.getContent());
            chunk.setStartIndex(c.getStartIndex());
            chunk.setEndIndex(c.getEndIndex());
            chunk.setStatus(ChunkStatus.PENDING);
            return chunk;
        }).toList();
        chunkRepository.saveAll(chunkEntities);
        doc.setStatus(DocumentStatus.CHUNKED);
        doc.setChunkedAt(Instant.now());
        documentRepository.saveAndFlush(doc);
        log.info("Document {} chunked into {} parts", documentId, chunkEntities.size());

        // Step 4: Embed
        List<EmbedRequest.EmbedDocumentItem> embedItems = chunkEntities.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", documentId);
            metadata.put("chunk_id", chunk.getId());
            metadata.put("source", doc.getName());
            metadata.put("start_index", chunk.getStartIndex());
            metadata.put("end_index", chunk.getEndIndex());
            return new EmbedRequest.EmbedDocumentItem(chunk.getId(), chunk.getContent(), metadata);
        }).toList();

        EmbedResponse embedResponse = embeddingClient.embed(new EmbedRequest(embedItems));
        if (embedResponse != null && embedResponse.ids() != null) {
            for (EmbedResponse.EmbedIdMapping mapping : embedResponse.ids()) {
                chunkRepository.findById(mapping.chunkId()).ifPresent(chunk -> {
                    chunk.setVectorStoreId(mapping.vectorStoreId());
                    chunk.setStatus(ChunkStatus.EMBEDDED);
                    chunkRepository.save(chunk);
                });
            }
        }

        doc.setStatus(DocumentStatus.EMBEDDED);
        doc.setEmbeddedAt(Instant.now());
        documentRepository.saveAndFlush(doc);
        log.info("Document {} embedded", documentId);
    }
}
