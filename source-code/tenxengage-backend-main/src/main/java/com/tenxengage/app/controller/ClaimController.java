package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.UnclaimRequest;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.dto.response.ClaimDetailResponse;
import com.tenxengage.app.dto.response.ClaimResponse;
import com.tenxengage.app.dto.response.ClaimSummaryResponse;
import com.tenxengage.app.entity.enums.ClaimStatus;
import com.tenxengage.app.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims", description = "Manage reward claims for purchase orders")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    @RequiresPermission("action.claim.view")
    @Operation(summary = "List claims", description = "Role-scoped paginated claim list")
    public ResponseEntity<Page<ClaimResponse>> getClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) UUID partnerCompanyId,
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 50, sort = "created_at") Pageable pageable) {
        return ResponseEntity.ok(claimService.getClaims(status, search, startDate, endDate,
                region, partnerCompanyId, userId, pageable));
    }

    @GetMapping("/{id}")
    @RequiresPermission("action.claim.view")
    @Operation(summary = "Get claim detail", description = "Detailed view with eligible/ineligible incentives")
    public ResponseEntity<ClaimDetailResponse> getClaimDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(claimService.getClaimDetail(id));
    }

    @PostMapping("/{id}/claim")
    @RequiresPermission("action.claim.submit")
    @Operation(summary = "Claim a deal", description = "Create a claim entry for the current user")
    @Audited(action = "Claimed", resourceType = "CLAIM", resourceId = "#id.toString()", description = "Claimed deal")
    public ResponseEntity<ClaimDetailResponse> claimDeal(@PathVariable UUID id) {
        return ResponseEntity.ok(claimService.claimDeal(id));
    }

    @PostMapping("/{id}/unclaim")
    @RequiresPermission("action.claim.unclaim")
    @Operation(summary = "Unclaim a deal", description = "Reverse all claims on a PO (admin only)")
    @Audited(action = "Unclaimed", resourceType = "CLAIM", resourceId = "#id.toString()", description = "Unclaimed deal")
    public ResponseEntity<ClaimDetailResponse> unclaimDeal(
            @PathVariable UUID id,
            @Valid @RequestBody UnclaimRequest request) {
        return ResponseEntity.ok(claimService.unclaimDeal(id, request));
    }

    @GetMapping("/summary")
    @RequiresPermission("action.claim.view")
    @Operation(summary = "Get claim summary", description = "Earnings summary and claim counts")
    public ResponseEntity<ClaimSummaryResponse> getClaimSummary(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(claimService.getClaimSummary(status, startDate, endDate, region));
    }
}
