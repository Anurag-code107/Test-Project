package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallet", description = "Reward wallet balance reads")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    @RequiresPermission("module.redemption_store")
    @Operation(summary = "Get my wallets", description = "Own individual wallet balances across all currencies")
    public ResponseEntity<List<RewardWalletResponse>> getMyWallets() {
        return ResponseEntity.ok(walletService.getMyWallets());
    }

    @GetMapping("/company/{companyId}")
    @RequiresPermission("module.redemption_store")
    @Operation(summary = "Get company wallets", description = "Partner company wallet balances — PARTNER_ADMIN (own company) or CLIENT_ADMIN (any company in tenant)")
    public ResponseEntity<List<RewardWalletResponse>> getCompanyWallets(@PathVariable UUID companyId) {
        return ResponseEntity.ok(walletService.getCompanyWallets(companyId));
    }

    @GetMapping("/users/{userId}")
    @RequiresPermission("action.redemption.view_all_history")
    @Operation(summary = "Get user wallets (admin)", description = "Individual wallet balances for any user in the tenant — CLIENT_ADMIN only")
    public ResponseEntity<List<RewardWalletResponse>> getUserWallets(@PathVariable UUID userId) {
        return ResponseEntity.ok(walletService.getUserWallets(userId));
    }
}
