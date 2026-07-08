package com.documind.document.infrastructure;

import com.documind.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
