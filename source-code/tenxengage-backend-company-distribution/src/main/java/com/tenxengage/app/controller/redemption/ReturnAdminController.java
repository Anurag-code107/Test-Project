package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.redemption.RejectReturnRequest;
import com.tenxengage.app.dto.request.redemption.ResolveTimedOutReturnRequest;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnQueueItemResponse;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Admin return review endpoints.
 * Base path: /api/v1/redemption/admin/returns
 * Permission: action.redemption.return.review (all endpoints)
 * Rate limit on approve+reject: 30 req/min per admin user (enforced externally).
 */
@RestController
@Validated
@RequestMapping("/api/v1/redemption/admin/returns")
@Tag(name = "Redemption Returns Admin", description = "Admin return request review and decision endpoints")
public class ReturnAdminController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "amount");

    // Native queries don't translate Java property names — map to DB column names explicitly.
    private static final java.util.Map<String, String> SORT_COLUMN_MAP = java.util.Map.of(
            "createdAt", "created_at",
            "amount",    "amount"
    );

    private final ReturnService returnService;
    private final TenantValidator tenantValidator;

    public ReturnAdminController(ReturnService returnService, TenantValidator tenantValidator) {
        this.returnService = returnService;
        this.tenantValidator = tenantValidator;
    }

    /**
     * GET /api/v1/redemption/admin/returns
     * Paginated return review queue for the current tenant.
     * Supports optional status, startDate, endDate, sortBy, sortDirection filters.
     */
    @GetMapping
    @RequiresPermission("action.redemption.return.review")
    @Operation(summary = "List return review queue (paginated)")
    public ResponseEntity<PaginatedResponse<ReturnQueueItemResponse>> getAdminReturns(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid sort field. Allowed values: createdAt, amount");
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sortDirection must be ASC or DESC", e);
        }

        UUID clientId = tenantValidator.getCurrentClientId();
        String sortColumn = SORT_COLUMN_MAP.getOrDefault(sortBy, sortBy);
        Page<ReturnQueueItemResponse> resultPage = returnService.getAdminReturns(
                clientId, status, startDate, endDate,
                PageRequest.of(page, size, Sort.by(direction, sortColumn)));

        return ResponseEntity.ok(PaginatedResponse.from(resultPage));
    }

    /**
     * GET /api/v1/redemption/admin/returns/{id}
     * Full return detail including admin-only fields (reviewNotes, vendorReturnReference).
     */
    @GetMapping("/{id}")
    @RequiresPermission("action.redemption.return.review")
    @Operation(summary = "Get return request detail (admin)")
    public ResponseEntity<ReturnDetailResponse> getReturnById(@PathVariable UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        ReturnDetailResponse response = returnService.getReturnById(id, null, clientId, true);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/redemption/admin/returns/{id}/approve
     * Approve a PENDING_APPROVAL return. Transitions to APPROVED;
     * fires async Xoxoday notification; writes APPROVED / REDEMPTION_RETURN audit.
     */
    @PostMapping("/{id}/approve")
    @RequiresPermission("action.redemption.return.review")
    @Operation(summary = "Approve a return request")
    @Audited(
            action = "APPROVED",
            resourceType = "REDEMPTION_RETURN",
            description = "Approved return request",
            resourceId = "#id?.toString()"
    )
    public ResponseEntity<ReturnDetailResponse> approveReturn(@PathVariable UUID id) {
        UUID reviewerId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();
        ReturnDetailResponse response = returnService.approveReturn(id, reviewerId, clientId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/redemption/admin/returns/{id}/reject
     * Reject a PENDING_APPROVAL return with a mandatory reason.
     * Transitions to RETURN_REJECTED; writes REJECTED / REDEMPTION_RETURN audit.
     */
    @PostMapping("/{id}/reject")
    @RequiresPermission("action.redemption.return.review")
    @Operation(summary = "Reject a return request")
    @Audited(
            action = "REJECTED",
            resourceType = "REDEMPTION_RETURN",
            description = "Rejected return request",
            resourceId = "#id?.toString()"
    )
    public ResponseEntity<ReturnDetailResponse> rejectReturn(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReturnRequest request) {
        UUID reviewerId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();
        ReturnDetailResponse response = returnService.rejectReturn(id, request, reviewerId, clientId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/redemption/admin/returns/{id}/resolve
     * Admin manual resolution of a RETURN_TIMED_OUT return.
     * CONFIRM path: credits wallet + transitions to RETURN_CONFIRMED; audit action = COMPLETED.
     * REJECT path: transitions to RETURN_REJECTED without wallet credit; audit action = REJECTED.
     * Both paths write a REDEMPTION_RETURN audit entry.
     *
     * Audit is written per-path inside ReturnService.resolveTimedOut() using AuditLogService.logAsync()
     * because a single @Audited annotation cannot branch on resolution value at annotation-processing time.
     * This follows the ApprovalService pattern for dual-action endpoints.
     */
    @PostMapping("/{id}/resolve")
    @RequiresPermission("action.redemption.return.review")
    @Operation(summary = "Manually resolve a RETURN_TIMED_OUT return (admin)")
    public ResponseEntity<ReturnDetailResponse> resolveTimedOutReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveTimedOutReturnRequest request) {
        UUID reviewerId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();
        ReturnDetailResponse response = returnService.resolveTimedOut(
                id, request.resolution(), request.notes(), reviewerId, clientId);
        return ResponseEntity.ok(response);
    }
}
