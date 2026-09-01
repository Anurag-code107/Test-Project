package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.FundCompanyWalletRequest;
import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.CompanyWalletFundingService;
import com.tenxengage.app.service.CompanyWalletXtrmSyncService;
import com.tenxengage.app.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallet", description = "Reward wallet balance reads")
public class WalletController {

    private final WalletService walletService;
    private final CompanyWalletFundingService fundingService;

    private final CompanyWalletXtrmSyncService companyWalletSync;

    public WalletController(WalletService walletService, CompanyWalletFundingService fundingService,
                            CompanyWalletXtrmSyncService companyWalletSync) {
        this.walletService = walletService;
        this.fundingService = fundingService;
        this.companyWalletSync = companyWalletSync;
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
        // Restate the balance from XTRM first — that is where a company's money actually is, and it is
        // funded there rather than here. Without this a funded company reads as "not funded yet", because
        // the row only ever appeared when someone credited it by hand. Never throws; a vendor blip leaves
        // the last known figure in place.
        companyWalletSync.syncIfConnected(companyId);
        return ResponseEntity.ok(walletService.getCompanyWallets(companyId));
    }

    @GetMapping("/users/{userId}")
    @RequiresPermission("action.redemption.view_all_history")
    @Operation(summary = "Get user wallets (admin)", description = "Individual wallet balances for any user in the tenant — CLIENT_ADMIN only")
    public ResponseEntity<List<RewardWalletResponse>> getUserWallets(@PathVariable UUID userId) {
        return ResponseEntity.ok(walletService.getUserWallets(userId));
    }

    /**
     * Fund a partner company's wallet — the API replacement for inserting into {@code reward_wallets} by hand.
     *
     * <p>Gated on {@code action.wallet.fund_company}, which is granted to CLIENT_ADMIN / PLATFORM_ADMIN and
     * deliberately <b>not</b> to PARTNER_ADMIN: they spend this wallet, so letting them top it up would remove
     * the only control on how much a company can distribute.</p>
     *
     * <p>Idempotent on {@code reference}. This is the only endpoint in the system that creates balance from
     * nothing, so the audit row is not optional and a double-submit must credit once.</p>
     */
    @PostMapping("/company/{companyId}/fund")
    @RequiresPermission("action.wallet.fund_company")
    @Audited(action = "FUNDED", resourceType = "REWARD_WALLET",
             resourceId = "#result.body.id.toString()",
             description = "Company wallet funded")
    @Operation(summary = "Fund a company wallet",
               description = "Credits a partner company's wallet, creating it on first funding. Idempotent on `reference`. CLIENT_ADMIN / PLATFORM_ADMIN only.")
    public ResponseEntity<RewardWalletResponse> fundCompanyWallet(
            @PathVariable UUID companyId,
            @Valid @RequestBody FundCompanyWalletRequest request) {
        return ResponseEntity.ok(fundingService.fund(companyId, request));
    }
}
