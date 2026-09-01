package com.tenxengage.app.service;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.UserActivityDocumentSubmission;
import com.tenxengage.app.entity.enums.DocumentSubmissionStatus;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.UserActivityDocumentSubmissionRepository;
import com.tenxengage.app.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for document submission and review workflows within activity-based incentives.
 * Handles submitting documents for review and processing reviewer decisions (approve/reject).
 */
@Service
public class ActivityDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ActivityDocumentService.class);

    private final UserActivityDocumentSubmissionRepository documentSubmissionRepository;
    private final IncentiveRepository incentiveRepository;
    private final ActivityCompletionService activityCompletionService;

    public ActivityDocumentService(
            UserActivityDocumentSubmissionRepository documentSubmissionRepository,
            IncentiveRepository incentiveRepository,
            ActivityCompletionService activityCompletionService) {
        this.documentSubmissionRepository = documentSubmissionRepository;
        this.incentiveRepository = incentiveRepository;
        this.activityCompletionService = activityCompletionService;
    }

    /**
     * Submits a document for a specific activity document requirement.
     * Creates a new submission with PENDING status. If a prior submission exists
     * for the same requirement, it is replaced (overwritten).
     *
     * @param userId                the user submitting the document
     * @param activityDefinitionId  the activity this document belongs to
     * @param documentRequirementId the specific document requirement being fulfilled
     * @param fileName              the original file name
     * @param filePath              the storage path of the uploaded file
     * @param fileSize              the file size in bytes
     * @return the created submission entity
     */
    @Transactional
    public UserActivityDocumentSubmission submitDocument(UUID userId, UUID activityDefinitionId,
                                                         UUID documentRequirementId,
                                                         String fileName, String filePath,
                                                         Long fileSize) {
        UUID clientId = TenantContext.getClientId();

        log.info("Document submission: user={}, activity={}, requirement={}, file={}",
                userId, activityDefinitionId, documentRequirementId, fileName);

        // Check if a prior submission exists for this requirement
        UserActivityDocumentSubmission existing = documentSubmissionRepository
                .findByClientIdAndUserIdAndDocumentRequirementId(clientId, userId, documentRequirementId)
                .orElse(null);

        if (existing != null) {
            // Replace the existing submission (reset to PENDING)
            existing.setFileName(fileName);
            existing.setFilePath(filePath);
            existing.setFileSize(fileSize);
            existing.setStatus(DocumentSubmissionStatus.PENDING);
            existing.setReviewedBy(null);
            existing.setReviewedAt(null);
            existing.setRejectionReason(null);

            log.info("Replaced existing document submission {}", existing.getId());
            return documentSubmissionRepository.save(existing);
        }

        // Create new submission
        UserActivityDocumentSubmission submission = UserActivityDocumentSubmission.builder()
                .clientId(clientId)
                .userId(userId)
                .activityDefinitionId(activityDefinitionId)
                .documentRequirementId(documentRequirementId)
                .fileName(fileName)
                .filePath(filePath)
                .fileSize(fileSize)
                .status(DocumentSubmissionStatus.PENDING)
                .build();

        UserActivityDocumentSubmission saved = documentSubmissionRepository.save(submission);
        log.info("Created document submission {} for requirement {}", saved.getId(), documentRequirementId);
        return saved;
    }

    /**
     * Reviews a document submission, updating its status to APPROVED or REJECTED.
     * If approved, triggers the activity completion check via ActivityCompletionService.
     *
     * @param submissionId    the submission to review
     * @param reviewerId      the ID of the reviewer
     * @param decision        "APPROVED" or "REJECTED"
     * @param rejectionReason optional reason for rejection (required if REJECTED)
     * @return the updated submission entity
     */
    @Transactional
    public UserActivityDocumentSubmission reviewSubmission(UUID submissionId, UUID reviewerId,
                                                           String decision, String rejectionReason) {
        UserActivityDocumentSubmission submission = documentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document submission not found: " + submissionId));

        DocumentSubmissionStatus newStatus = DocumentSubmissionStatus.valueOf(decision.toUpperCase());

        submission.setStatus(newStatus);
        submission.setReviewedBy(reviewerId);
        submission.setReviewedAt(Instant.now());

        if (newStatus == DocumentSubmissionStatus.REJECTED) {
            submission.setRejectionReason(rejectionReason);
        } else {
            submission.setRejectionReason(null);
        }

        UserActivityDocumentSubmission updated = documentSubmissionRepository.save(submission);

        log.info("Reviewed submission {}: status={}, reviewer={}",
                submissionId, newStatus, reviewerId);

        // If approved, trigger activity completion check
        if (newStatus == DocumentSubmissionStatus.APPROVED) {
            UUID incentiveId = resolveIncentiveId(submission.getActivityDefinitionId());
            activityCompletionService.onDocumentApproved(
                    submission.getClientId(),
                    submission.getUserId(),
                    submission.getActivityDefinitionId(),
                    incentiveId);
        }

        return updated;
    }

    /**
     * Lists all document submissions for a given user and activity.
     *
     * @param userId               the user whose submissions to retrieve
     * @param activityDefinitionId the activity to filter by
     * @return list of document submissions
     */
    @Transactional(readOnly = true)
    public List<UserActivityDocumentSubmission> getSubmissions(UUID userId,
                                                               UUID activityDefinitionId) {
        UUID clientId = TenantContext.getClientId();
        return documentSubmissionRepository
                .findByClientIdAndUserIdAndActivityDefinitionId(clientId, userId, activityDefinitionId);
    }

    /**
     * Resolves the incentive ID from an activity definition by querying
     * the incentive that owns the activity definition via JOIN.
     */
    private UUID resolveIncentiveId(UUID activityDefinitionId) {
        Incentive incentive = incentiveRepository.findByActivityDefinitionId(activityDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No incentive found for activity definition: " + activityDefinitionId));
        return incentive.getId();
    }
}
