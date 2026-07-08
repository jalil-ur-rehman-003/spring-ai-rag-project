package com.documind.document.web;

import com.documind.auth.domain.User;
import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.common.error.EntityNotFoundException;
import com.documind.document.application.DocumentUploadService;
import com.documind.document.application.UnsupportedDocumentFileException;
import com.documind.document.domain.Document;
import com.documind.document.domain.DocumentVisibility;
import com.documind.document.infrastructure.DocumentRepository;
import com.documind.org.application.OrganizationService;
import com.documind.org.domain.Organization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final OrganizationService organizationService;
    private final DocumentRepository documentRepository;

    public DocumentController(
            DocumentUploadService documentUploadService, OrganizationService organizationService,
            DocumentRepository documentRepository
    ) {
        this.documentUploadService = documentUploadService;
        this.organizationService = organizationService;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public List<DocumentListItemResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return documentRepository.findByOrganizationIdOrderByCreatedAtDesc(principal.getOrganizationId()).stream()
                .map(this::toListItemResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "visibility", defaultValue = "PRIVATE") DocumentVisibility visibility,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Organization organization = organizationService.findByIdOrThrow(principal.getOrganizationId());
        User uploader = principal.getUser();

        Document document = documentUploadService.acceptUpload(
                organization, uploader, file.getOriginalFilename(), file.getContentType(),
                openStream(file), file.getSize(), visibility
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new DocumentUploadResponse(document.getId(), document.getStatus()));
    }

    private static java.io.InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read uploaded file content", exception);
        }
    }

    private DocumentListItemResponse toListItemResponse(Document document) {
        return new DocumentListItemResponse(
                document.getId(), document.getTitle(), document.getOriginalFilename(), document.getSizeBytes(),
                document.getVisibility(), document.getStatus(), document.getFailureReason(), document.getCreatedAt()
        );
    }
}
