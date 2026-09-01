package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.RewardTransactionResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.RewardTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reward-transactions")
@Tag(name = "Reward Transactions", description = "Per-user reward transaction history (earned-only today)")
public class RewardTransactionController {

    private final RewardTransactionService rewardTransactionService;
    private final TenantValidator tenantValidator;

    public RewardTransactionController(RewardTransactionService rewardTransactionService,
                                       TenantValidator tenantValidator) {
        this.rewardTransactionService = rewardTransactionService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reward transactions",
            description = "Paginated transactions for the authenticated user, ordered by date descending")
    public ResponseEntity<Page<RewardTransactionResponse>> getMyTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                rewardTransactionService.getTransactions(clientId, userId, startDate, endDate, pageable));
    }

    @GetMapping("/{userId}")
    @RequiresPermission("action.reward_balances.admin")
    @Operation(summary = "Get user reward transactions (admin)",
            description = "Paginated transactions for the specified user")
    public ResponseEntity<Page<RewardTransactionResponse>> getUserTransactions(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return ResponseEntity.ok(
                rewardTransactionService.getTransactions(clientId, userId, startDate, endDate, pageable));
    }
}
