package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.dto.request.redemption.RedemptionAdminHistoryFilters;
import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionAdminHistoryResponse;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.RedemptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/requests")
@Tag(name = "Redemption History", description = "Redemption transaction history")
public class RedemptionHistoryController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("submittedAt", "amount", "status");

    private final RedemptionHistoryService historyService;
    private final TenantValidator tenantValidator;

    public RedemptionHistoryController(RedemptionHistoryService historyService,
                                       TenantValidator tenantValidator) {
        this.historyService = historyService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/company")
    @RequiresPermission("action.redemption.redeem_company")
    @Operation(summary = "List company redemption history (PARTNER_ADMIN only)")
    public ResponseEntity<PaginatedResponse<RedemptionRequestResponse>> listCompanyRedemptions(
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

        UUID userId = tenantValidator.getCurrentUserId();
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(status, category, dateFrom, dateTo);

        return ResponseEntity.ok(PaginatedResponse.from(
                historyService.getCompanyHistory(
                        userId, filters, PageRequest.of(page, pageSize, Sort.by(direction, sortBy)))));
    }

    @GetMapping("/all")
    @RequiresPermission("action.redemption.view_all_history")
    @Operation(summary = "List all-tenant redemption history (CLIENT_ADMIN)")
    public ResponseEntity<PaginatedResponse<RedemptionAdminHistoryResponse>> listTenantRedemptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) RedemptionStatus status,
            @RequestParam(required = false) RedemptionCategory category,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID companyId) {

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
        RedemptionAdminHistoryFilters filters = new RedemptionAdminHistoryFilters(
                status, category, dateFrom, dateTo, userId, companyId);

        return ResponseEntity.ok(PaginatedResponse.from(
                historyService.getTenantHistory(
                        filters, PageRequest.of(page, pageSize, Sort.by(direction, sortBy)))));
    }
}
