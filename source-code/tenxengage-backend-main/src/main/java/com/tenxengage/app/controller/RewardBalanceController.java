package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.RewardBalanceResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Deprecated
@RestController
@RequestMapping("/api/v1/reward-balances")
@Tag(name = "Reward Balances", description = "Deprecated — use /api/v1/wallets instead")
public class RewardBalanceController {

    private final WalletService walletService;

    public RewardBalanceController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Deprecated
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my balances (deprecated)", description = "Delegates to /api/v1/wallets/me. Use that endpoint instead.")
    public ResponseEntity<List<RewardBalanceResponse>> getMyBalances() {
        return ResponseEntity.ok(
            walletService.getMyWallets().stream()
                .map(RewardBalanceResponse::fromWallet)
                .toList()
        );
    }

    @Deprecated
    @GetMapping("/{userId}")
    @RequiresPermission("action.redemption.view_all_history")
    @Operation(summary = "Get user balances (deprecated)", description = "Delegates to /api/v1/wallets/users/{userId}. Use that endpoint instead.")
    public ResponseEntity<List<RewardBalanceResponse>> getUserBalances(@PathVariable UUID userId) {
        return ResponseEntity.ok(
            walletService.getUserWallets(userId).stream()
                .map(RewardBalanceResponse::fromWallet)
                .toList()
        );
    }
}
