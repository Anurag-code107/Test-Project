package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserActivityDocumentSubmission;
import com.tenxengage.app.entity.enums.DocumentSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserActivityDocumentSubmissionRepository
        extends JpaRepository<UserActivityDocumentSubmission, UUID> {

    List<UserActivityDocumentSubmission> findByClientIdAndUserIdAndActivityDefinitionId(
            UUID clientId, UUID userId, UUID activityDefinitionId);

    Optional<UserActivityDocumentSubmission> findByClientIdAndUserIdAndDocumentRequirementId(
            UUID clientId, UUID userId, UUID documentRequirementId);

    long countByClientIdAndUserIdAndActivityDefinitionIdAndStatus(
            UUID clientId, UUID userId, UUID activityDefinitionId, DocumentSubmissionStatus status);
}
