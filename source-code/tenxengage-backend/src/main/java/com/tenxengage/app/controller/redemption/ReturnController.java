package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnSummaryResponse;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/redemption/returns")
@Tag(name = "Redemption Returns", description = "Partner return request submission and management")
public class ReturnController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "amount");

    private static final java.util.Map<String, String> SORT_COLUMN_MAP = java.util.Map.of(
            "createdAt", "created_at",
            "amount",    "amount"
    );

    private final ReturnService returnService;
    private final TenantValidator tenantValidator;

    public ReturnController(ReturnService returnService, TenantValidator tenantValidator) {
        this.returnService = returnService;
        this.tenantValidator = tenantValidator;
    }

    /**
     * POST /api/v1/redemption/returns
     * Submit a new return request. Rate limited to 5 req/min per tenant (enforced externally).
     */
    @PostMapping
    @RequiresPermission("action.redemption.return.request")
    @Operation(summary = "Submit a return request")
    @Audited(
            action = "SUBMITTED",
            resourceType = "REDEMPTION_RETURN",
            description = "Partner submitted return request",
            resourceId = "#result.body?.id?.toString()"
    )
    public ResponseEntity<ReturnDetailResponse> submitReturn(
            @Valid @RequestBody SubmitReturnRequest request) {

        UUID userId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();

        ReturnDetailResponse response = returnService.submitReturn(request, userId, clientId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * GET /api/v1/redemption/returns
     * List the calling partner's own return requests, paginated.
     */
    @GetMapping
    @RequiresPermission("action.redemption.return.request")
    @Operation(summary = "List own return requests (paginated)")
    public ResponseEntity<PaginatedResponse<ReturnSummaryResponse>> listReturns(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid sort field. Allowed values: createdAt, amount");
        }

        UUID userId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortDirection must be ASC or DESC", e);
        }
        String sortColumn = SORT_COLUMN_MAP.getOrDefault(sortBy, sortBy);
        Page<ReturnSummaryResponse> resultPage = returnService.getPartnerReturns(
                userId, clientId, status, PageRequest.of(page, size, Sort.by(direction, sortColumn)));

        return ResponseEntity.ok(PaginatedResponse.from(resultPage));
    }

    /**
     * GET /api/v1/redemption/returns/{id}
     * Get detail for the calling partner's own return.
     */
    @GetMapping("/{id}")
    @RequiresPermission("action.redemption.return.request")
    @Operation(summary = "Get own return request detail")
    public ResponseEntity<ReturnDetailResponse> getReturn(
            @PathVariable UUID id) {

        UUID userId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();

        ReturnDetailResponse response = returnService.getReturnById(id, userId, clientId, false);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/redemption/returns/{id}
     * Cancel a PENDING_APPROVAL return (partner action).
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("action.redemption.return.request")
    @Operation(summary = "Cancel own return request")
    @Audited(
            action = "CANCELLED",
            resourceType = "REDEMPTION_RETURN",
            description = "Partner cancelled return request",
            resourceId = "#id?.toString()"
    )
    public ResponseEntity<Void> cancelReturn(@PathVariable UUID id) {

        UUID userId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();

        returnService.cancelReturn(id, userId, clientId);
        return ResponseEntity.noContent().build();
    }
}
