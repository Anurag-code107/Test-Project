package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateIncentiveRequest;
import com.tenxengage.app.dto.request.UpdateIncentiveRequest;
import com.tenxengage.app.dto.request.UpdateIncentiveStatusRequest;
import com.tenxengage.app.dto.response.ApprovalStatusResponse;
import com.tenxengage.app.dto.response.DocumentSummaryResponse;
import com.tenxengage.app.dto.response.ForecastResponse;
import com.tenxengage.app.dto.response.IncentiveDetailResponse;
import com.tenxengage.app.dto.response.IncentiveResponse;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.service.IncentiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.tenxengage.app.security.RequiresPermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import com.tenxengage.app.security.TenantValidator;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incentives")
@Tag(name = "Incentives", description = "Incentive program management — scoped by tenant")
@Validated
public class IncentiveController {

    private final IncentiveService incentiveService;
    private final TenantValidator tenantValidator;

    public IncentiveController(IncentiveService incentiveService,
                               TenantValidator tenantValidator) {
        this.incentiveService = incentiveService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @Operation(summary = "List incentives", description = "Get paginated list with optional type, status, and search filters")
    @RequiresPermission("action.incentive.view")
    public ResponseEntity<Page<IncentiveResponse>> getIncentives(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Filter by incentive type")
            @RequestParam(required = false) IncentiveType type,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) IncentiveStatus status,
            @Parameter(description = "Search by name")
            @RequestParam(required = false) @Size(max = 255) String search) {
        var userDetails = tenantValidator.getCurrentUserDetails();
        boolean isAdmin = userDetails == null
            || userDetails.isTenxAdmin()
            || userDetails.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return auth.contains("CLIENT_ADMIN") || auth.contains("TENX_ADMIN");
                })
            || userDetails.getPartnerCompanyId() == null;

        Page<IncentiveResponse> incentives;
        if (isAdmin) {
            incentives = incentiveService.getIncentives(type, status, search, pageable);
        } else {
            incentives = incentiveService.getIncentivesForPartner(type, search, pageable);
        }
        return ResponseEntity.ok(incentives);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incentive details", description = "Get full incentive detail including nested entities")
    @RequiresPermission("action.incentive.view")
    public ResponseEntity<IncentiveDetailResponse> getIncentiveById(@PathVariable UUID id) {
        IncentiveDetailResponse incentive = incentiveService.getIncentiveById(id);
        return ResponseEntity.ok(incentive);
    }

    @PostMapping
    @Operation(summary = "Create incentive", description = "Create a new incentive with all nested data")
    @RequiresPermission("action.incentive.create")
    @Audited(action = "Created", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<IncentiveDetailResponse> createIncentive(
            @Valid @RequestBody CreateIncentiveRequest request) {
        IncentiveDetailResponse incentive = incentiveService.createIncentive(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(incentive);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update incentive", description = "Update incentive with optional nested data")
    @RequiresPermission("action.incentive.edit")
    @Audited(action = "Edited", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<IncentiveDetailResponse> updateIncentive(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncentiveRequest request) {
        IncentiveDetailResponse incentive = incentiveService.updateIncentive(id, request);
        return ResponseEntity.ok(incentive);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete incentive")
    @RequiresPermission("action.incentive.delete")
    @Audited(action = "Deleted", resourceType = "INCENTIVE", resourceId = "#id.toString()")
    public ResponseEntity<Void> deleteIncentive(@PathVariable UUID id) {
        incentiveService.deleteIncentive(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update incentive status", description = "Transition incentive to a new status")
    @RequiresPermission("action.incentive.activate")
    @Audited(action = "Edited", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()", description = "Status updated")
    public ResponseEntity<IncentiveResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncentiveStatusRequest request) {
        IncentiveResponse incentive = incentiveService.updateStatus(id, request);
        return ResponseEntity.ok(incentive);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit for approval", description = "Submit a DRAFT incentive for approval — sends emails to configured approvers")
    @RequiresPermission("action.incentive.submit_approval")
    @Audited(action = "Submitted", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<IncentiveResponse> submitForApproval(@PathVariable UUID id) {
        IncentiveResponse incentive = incentiveService.submitForApproval(id);
        return ResponseEntity.ok(incentive);
    }

    @PostMapping("/{id}/resend-approvals")
    @Operation(summary = "Resend approval emails", description = "Resend approval emails to pending approvers for a PENDING_APPROVAL incentive")
    @RequiresPermission("action.incentive.resend_approval")
    public ResponseEntity<Void> resendApprovalEmails(@PathVariable UUID id) {
        incentiveService.resendApprovalEmails(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/approval-status")
    @Operation(summary = "Get approval status", description = "Get per-approver decision status for an incentive")
    @RequiresPermission("action.incentive.submit_approval")
    public ResponseEntity<ApprovalStatusResponse> getApprovalStatus(@PathVariable UUID id) {
        ApprovalStatusResponse status = incentiveService.getApprovalStatus(id);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/{id}/resend-approval")
    @Operation(summary = "Resend approval to single approver", description = "Resend approval email to a specific pending approver")
    @RequiresPermission("action.incentive.resend_approval")
    public ResponseEntity<Void> resendApprovalToApprover(
            @PathVariable UUID id,
            @RequestParam @NotBlank @Email String email) {
        incentiveService.resendApprovalToApprover(id, email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "Resubmit denied incentive for approval", description = "Clear previous decisions and resubmit a DENIED incentive for approval")
    @RequiresPermission("action.incentive.submit_approval")
    @Audited(action = "Submitted", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()", description = "Resubmitted for approval")
    public ResponseEntity<IncentiveResponse> resubmitForApproval(@PathVariable UUID id) {
        IncentiveResponse response = incentiveService.resubmitForApproval(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "Clone incentive", description = "Deep clone an existing incentive as a new draft")
    @RequiresPermission("action.incentive.clone")
    @Audited(action = "Created", resourceType = "INCENTIVE", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()", description = "Cloned incentive")
    public ResponseEntity<IncentiveDetailResponse> cloneIncentive(
            @PathVariable UUID id,
            @Parameter(description = "Name for the cloned incentive")
            @RequestParam @NotBlank @Size(max = 255) String name,
            @Parameter(description = "Optional description override")
            @RequestParam(required = false) @Size(max = 2000) String description) {
        IncentiveDetailResponse incentive = incentiveService.cloneIncentive(id, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(incentive);
    }

    @PostMapping(value = "/{id}/forecast", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Generate forecast", description = "Generate AI forecast for an incentive (SSE stream)")
    @RequiresPermission("action.incentive.forecast")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter generateForecast(@PathVariable UUID id) {
        return incentiveService.generateForecastStreaming(id);
    }

    @GetMapping("/{id}/forecast")
    @Operation(summary = "Get forecast", description = "Get the most recent forecast for an incentive")
    @RequiresPermission("action.incentive.forecast")
    public ResponseEntity<ForecastResponse> getForecast(@PathVariable UUID id) {
        ForecastResponse forecast = incentiveService.getLatestForecast(id);
        if (forecast == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(forecast);
    }

    @PostMapping(value = "/forecast-preview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Preview forecast", description = "Generate AI forecast preview from builder state (no saved incentive required)")
    @RequiresPermission("action.incentive.forecast")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter forecastPreview(
            @Valid @RequestBody com.tenxengage.app.dto.request.ForecastPreviewRequest request) {
        return incentiveService.generateForecastPreview(request);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload documents", description = "Upload program documents for an incentive")
    @RequiresPermission("action.incentive.documents.upload")
    @Audited(action = "Uploaded", resourceType = "INCENTIVE", resourceId = "#id.toString()", description = "Uploaded documents")
    public ResponseEntity<List<DocumentSummaryResponse>> uploadDocuments(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "categories", required = false) List<String> categories) {
        List<DocumentSummaryResponse> documents = incentiveService.uploadDocuments(id, files, categories);
        return ResponseEntity.status(HttpStatus.CREATED).body(documents);
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @Operation(summary = "Download document", description = "Download a document file from an incentive")
    @RequiresPermission("action.incentive.documents.download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId) {
        IncentiveService.DownloadResult result = incentiveService.downloadDocument(id, documentId);

        String contentType = switch (result.fileType()) {
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            default -> "application/octet-stream";
        };

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFilename(result.filename()) + "\"")
            .body(new InputStreamResource(result.inputStream()));
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null) return "download";
        return filename.replaceAll("[\"\\r\\n]", "_");
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @Operation(summary = "Delete document", description = "Remove a document from an incentive")
    @RequiresPermission("action.incentive.documents.delete")
    @Audited(action = "Deleted", resourceType = "INCENTIVE", resourceId = "#id.toString()", description = "Deleted document")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId) {
        incentiveService.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}
