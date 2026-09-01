package com.tenxengage.app.controller;

import com.tenxengage.app.entity.UserActivityDocumentSubmission;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ActivityDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-documents")
@Tag(name = "Activity Documents", description = "Submit and review activity document submissions")
public class ActivityDocumentController {

    private final ActivityDocumentService activityDocumentService;
    private final TenantValidator tenantValidator;

    public ActivityDocumentController(ActivityDocumentService activityDocumentService,
                                      TenantValidator tenantValidator) {
        this.activityDocumentService = activityDocumentService;
        this.tenantValidator = tenantValidator;
    }

    @PostMapping("/{activityDefinitionId}/submit")
    @RequiresPermission("action.incentive.view")
    @Operation(summary = "Submit a document", description = "Submit a document for an activity requirement")
    public ResponseEntity<UserActivityDocumentSubmission> submitDocument(
            @PathVariable UUID activityDefinitionId,
            @RequestBody Map<String, Object> body) {
        UUID userId = tenantValidator.getCurrentUserId();
        UUID documentRequirementId = UUID.fromString((String) body.get("documentRequirementId"));
        String fileName = (String) body.get("fileName");
        String filePath = (String) body.get("filePath");
        Long fileSize = body.get("fileSize") != null
                ? ((Number) body.get("fileSize")).longValue() : null;

        UserActivityDocumentSubmission submission = activityDocumentService.submitDocument(
                userId, activityDefinitionId, documentRequirementId,
                fileName, filePath, fileSize);

        return ResponseEntity.ok(submission);
    }

    @GetMapping("/{activityDefinitionId}/submissions")
    @RequiresPermission("action.incentive.view")
    @Operation(summary = "List submissions", description = "List document submissions for an activity")
    public ResponseEntity<List<UserActivityDocumentSubmission>> getSubmissions(
            @PathVariable UUID activityDefinitionId) {
        UUID userId = tenantValidator.getCurrentUserId();
        List<UserActivityDocumentSubmission> submissions =
                activityDocumentService.getSubmissions(userId, activityDefinitionId);
        return ResponseEntity.ok(submissions);
    }

    @PutMapping("/submissions/{submissionId}/review")
    @RequiresPermission("action.incentive.manage")
    @Operation(summary = "Review a submission", description = "Approve or reject a document submission")
    public ResponseEntity<UserActivityDocumentSubmission> reviewSubmission(
            @PathVariable UUID submissionId,
            @RequestBody Map<String, String> body) {
        UUID reviewerId = tenantValidator.getCurrentUserId();
        String decision = body.get("decision");
        String rejectionReason = body.get("rejectionReason");

        UserActivityDocumentSubmission updated = activityDocumentService.reviewSubmission(
                submissionId, reviewerId, decision, rejectionReason);

        return ResponseEntity.ok(updated);
    }
}
