package com.demo.rag.controller;

import com.demo.rag.dto.DocumentDetailDto;
import com.demo.rag.dto.DocumentSummaryDto;
import com.demo.rag.entity.Document;
import com.demo.rag.entity.DocumentStatus;
import com.demo.rag.repository.DocumentRepository;
import com.demo.rag.service.DocumentIngestService;
import com.demo.rag.service.DocumentQueryService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentIngestService ingestService;
    private final DocumentQueryService queryService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSummaryDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        String extension = getExtension(originalName);
        byte[] bytes = file.getBytes();

        Document doc = new Document();
        doc.setName(originalName);
        doc.setFileType(extension);
        doc.setFileSize(file.getSize());
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedAt(Instant.now());
        doc = documentRepository.save(doc);

        ingestService.processDocumentAsync(doc.getId(), bytes, extension);

        return ResponseEntity.ok(new DocumentSummaryDto(
                doc.getId(),
                doc.getName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getUploadedAt(),
                0
        ));
    }

    @GetMapping
    public List<DocumentSummaryDto> list() {
        return queryService.listAll();
    }

    @GetMapping("/{id}")
    public DocumentDetailDto detail(@PathVariable Long id) {
        return queryService.getDetail(id);
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("File must have an extension");
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
