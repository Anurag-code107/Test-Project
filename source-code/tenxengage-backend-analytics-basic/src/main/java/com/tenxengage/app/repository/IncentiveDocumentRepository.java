package com.tenxengage.app.repository;

import com.tenxengage.app.entity.IncentiveDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncentiveDocumentRepository extends JpaRepository<IncentiveDocument, UUID> {
}
