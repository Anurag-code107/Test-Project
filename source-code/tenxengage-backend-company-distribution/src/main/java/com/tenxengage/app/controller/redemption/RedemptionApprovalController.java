package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.redemption.RejectRedemptionRequest;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.redemption.ApprovalQueueItemResponse;
import com.tenxengage.app.entity.enums.RedemptionRequestType;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.RedemptionApprovalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/requests")
@Tag(name = "Redemption Approval Queue")
public class RedemptionApprovalController {

    private final RedemptionApprovalService approvalService;
    private final TenantValidator tenantValidator;

    public RedemptionApprovalController(RedemptionApprovalService approvalService,
                                        TenantValidator tenantValidator) {
        this.approvalService = approvalService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/approval-queue")
    @RequiresPermission("action.redemption.approve")
    public ResponseEntity<PaginatedResponse<ApprovalQueueItemResponse>> getApprovalQueue(
            @RequestParam(required = false) String currencyId,
            @RequestParam(required = false) UUID catalogItemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) RedemptionRequestType requestType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 50) {
            throw new IllegalArgumentException("Page size must not exceed 50");
        }

        return ResponseEntity.ok(PaginatedResponse.from(
                approvalService.getApprovalQueue(
                        currencyId, catalogItemId, startDate, endDate, requestType,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")))));
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission("action.redemption.approve")
    @Audited(action = "APPROVED", resourceType = "REDEMPTION_REQUEST",
             description = "Approved redemption request")
    public ResponseEntity<RedemptionRequestDetailResponse> approveRedemption(@PathVariable UUID id) {
        UUID approverId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(approvalService.approveRedemption(id, approverId));
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission("action.redemption.approve")
    @Audited(action = "REJECTED", resourceType = "REDEMPTION_REQUEST",
             description = "Rejected redemption request")
    public ResponseEntity<RedemptionRequestDetailResponse> rejectRedemption(
            @PathVariable UUID id,
            @RequestBody @Valid RejectRedemptionRequest request) {
        UUID approverId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(approvalService.rejectRedemption(id, request.rejectionReason(), approverId));
    }
}
