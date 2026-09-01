package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.BankTransferRedemptionRequest;
import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.RedemptionSubmissionConfirmationResponse;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.RedemptionSubmissionService;
import com.tenxengage.app.service.redemption.RedemptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/requests")
@Tag(name = "Redemption Flow", description = "Partner redemption request submission and history")
public class RedemptionRequestController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("submittedAt", "amount", "status");

    private final RedemptionSubmissionService submissionService;
    private final RedemptionHistoryService historyService;
    private final TenantValidator tenantValidator;

    public RedemptionRequestController(RedemptionSubmissionService submissionService,
                                       RedemptionHistoryService historyService,
                                       TenantValidator tenantValidator) {
        this.submissionService = submissionService;
        this.historyService = historyService;
        this.tenantValidator = tenantValidator;
    }

    @PostMapping
    @RequiresPermission("action.redemption.redeem")
    @Audited(action = "SUBMITTED", resourceType = "REDEMPTION_REQUEST",
             resourceId = "#result.body.id.toString()",
             description = "Partner submitted personal wallet redemption")
    @Operation(summary = "Submit personal wallet redemption")
    public ResponseEntity<RedemptionSubmissionConfirmationResponse> submitPersonalRedemption(
            @Valid @RequestBody SubmitPersonalRedemptionRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        RedemptionSubmissionConfirmationResponse response =
                submissionService.submitPersonalRedemption(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @RequiresPermission("action.redemption.view_history")
    @Operation(summary = "List personal redemption requests")
    public ResponseEntity<PaginatedResponse<RedemptionRequestResponse>> listRedemptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) RedemptionStatus status,
            @RequestParam(required = false) RedemptionCategory category,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        if (pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must not exceed 50");
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field: " + sortBy);
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "dateFrom must not be after dateTo");
        }
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        UUID userId = tenantValidator.getCurrentUserId();
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(status, category, dateFrom, dateTo);
        return ResponseEntity.ok(PaginatedResponse.from(
                historyService.getPersonalHistory(
                        userId, filters, PageRequest.of(page, pageSize, Sort.by(direction, sortBy)))));
    }

    @GetMapping("/{id}")
    @RequiresPermission({"action.redemption.view_history", "action.redemption.view_all_history"})
    @Operation(summary = "Get redemption request detail")
    public ResponseEntity<RedemptionRequestDetailResponse> getRedemption(@PathVariable UUID id) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(historyService.getRedemptionDetail(id, userId));
    }
}
