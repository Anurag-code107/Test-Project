package com.tenxengage.app.controller.xtrm;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.xtrm.AddCardRequest;
import com.tenxengage.app.dto.request.xtrm.ConfirmWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.InitiateWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.LinkBankAccountRequest;
import com.tenxengage.app.dto.request.xtrm.SaveRedemptionAddressRequest;
import com.tenxengage.app.dto.request.xtrm.SetDefaultBankRequest;
import com.tenxengage.app.dto.request.xtrm.SetDefaultCardRequest;
import com.tenxengage.app.dto.request.xtrm.SetPayoutMethodRequest;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.xtrm.DigitalWalletResponse;
import com.tenxengage.app.dto.response.xtrm.LinkedBankResponse;
import com.tenxengage.app.dto.response.xtrm.LinkedCardResponse;
import com.tenxengage.app.dto.response.xtrm.RedemptionProfileResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalHistoryResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalResultResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmBankService;
import com.tenxengage.app.service.xtrm.XtrmCardService;
import com.tenxengage.app.service.xtrm.XtrmEnrollmentService;
import com.tenxengage.app.service.xtrm.XtrmWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service XTRM payout profile for the current user (F-03 enhancement).
 *
 * <p>Every endpoint resolves the {@code partner_redemption} row from the JWT — there is no id in the path or
 * body (IDOR-guarded, self-only). Access requires either {@code action.redemption.redeem} (personal payee)
 * or {@code action.redemption.redeem_company} (company payee). Responses never expose the PAT, the linked
 * bank id, or any account/routing number — only a masked label.</p>
 */
@RestController
@RequestMapping("/api/v1/redemption/profile")
@Tag(name = "Redemption Payout", description = "Self-service XTRM payout profile: enrollment, payout method, bank linking")
public class RedemptionProfileController {

    private final XtrmEnrollmentService enrollmentService;
    private final XtrmBankService bankService;
    private final XtrmCardService cardService;
    private final XtrmWalletService walletService;
    private final TenantValidator tenantValidator;

    public RedemptionProfileController(XtrmEnrollmentService enrollmentService,
                                       XtrmBankService bankService,
                                       XtrmCardService cardService,
                                       XtrmWalletService walletService,
                                       TenantValidator tenantValidator) {
        this.enrollmentService = enrollmentService;
        this.bankService = bankService;
        this.cardService = cardService;
        this.walletService = walletService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "Get the current user's redemption payout profile")
    public ResponseEntity<RedemptionProfileResponse> getProfile() {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(RedemptionProfileResponse.from(enrollmentService.getProfileView(userId)));
    }

    @PutMapping("/address")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "Save the payout address and enroll for payouts")
    public ResponseEntity<RedemptionProfileResponse> saveAddress(
            @Valid @RequestBody SaveRedemptionAddressRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                RedemptionProfileResponse.from(enrollmentService.saveAddressAndEnroll(userId, request)));
    }

    @PutMapping("/payout-method")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "EDITED", resourceType = "PARTNER_REDEMPTION", description = "Updated payout method")
    @Operation(summary = "Set the payout method (AnyPay, Bank, or Card)")
    public ResponseEntity<RedemptionProfileResponse> setPayoutMethod(
            @Valid @RequestBody SetPayoutMethodRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                RedemptionProfileResponse.from(bankService.setPayoutMethod(userId, request.payoutMethod())));
    }

    @GetMapping("/banks")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "List the current user's linked bank accounts")
    public ResponseEntity<List<LinkedBankResponse>> listBanks() {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(bankService.listBanks(userId));
    }

    @PostMapping("/bank-account")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "BANK_LINKED", resourceType = "PARTNER_REDEMPTION", description = "Linked bank account")
    @Operation(summary = "Add a bank account for ACH/Bank payouts")
    public ResponseEntity<RedemptionProfileResponse> addBankAccount(
            @Valid @RequestBody LinkBankAccountRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        RedemptionProfileResponse response =
                RedemptionProfileResponse.from(bankService.addBank(userId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/banks/{bankId}")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "BANK_UNLINKED", resourceType = "PARTNER_REDEMPTION", description = "Removed bank account")
    @Operation(summary = "Remove a specific linked bank account")
    public ResponseEntity<RedemptionProfileResponse> removeBankAccount(@PathVariable UUID bankId) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(RedemptionProfileResponse.from(bankService.removeBank(userId, bankId)));
    }

    @PutMapping("/banks/default")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "EDITED", resourceType = "PARTNER_REDEMPTION", description = "Set default bank account")
    @Operation(summary = "Set the default bank account for Bank payouts")
    public ResponseEntity<RedemptionProfileResponse> setDefaultBank(
            @Valid @RequestBody SetDefaultBankRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                RedemptionProfileResponse.from(bankService.setDefaultBank(userId, request.bankId())));
    }

    @GetMapping("/wallets")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "List my XTRM digital wallets (view-only)")
    public ResponseEntity<List<DigitalWalletResponse>> listWallets() {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(walletService.listWallets(userId));
    }

    // ---------------------------------------------------------------------
    // Linked cards (F-03 multi-card) — a card is both a payout rail and a withdrawal destination.
    // ---------------------------------------------------------------------

    @GetMapping("/cards")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "List the current user's linked cards")
    public ResponseEntity<List<LinkedCardResponse>> listCards() {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(cardService.listCards(userId));
    }

    @PostMapping("/card")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "CARD_LINKED", resourceType = "PARTNER_REDEMPTION", description = "Linked card")
    @Operation(summary = "Link a card for card payouts / withdrawals")
    public ResponseEntity<RedemptionProfileResponse> addCard(@Valid @RequestBody AddCardRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        RedemptionProfileResponse response =
                RedemptionProfileResponse.from(cardService.addCard(userId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/cards/{cardId}")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "CARD_UNLINKED", resourceType = "PARTNER_REDEMPTION", description = "Removed card")
    @Operation(summary = "Remove a specific linked card")
    public ResponseEntity<RedemptionProfileResponse> removeCard(@PathVariable UUID cardId) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(RedemptionProfileResponse.from(cardService.removeCard(userId, cardId)));
    }

    @PutMapping("/cards/default")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "EDITED", resourceType = "PARTNER_REDEMPTION", description = "Set default card")
    @Operation(summary = "Set the default card for Card payouts")
    public ResponseEntity<RedemptionProfileResponse> setDefaultCard(
            @Valid @RequestBody SetDefaultCardRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                RedemptionProfileResponse.from(cardService.setDefaultCard(userId, request.cardId())));
    }

    // ---------------------------------------------------------------------
    // Wallet withdrawal (F-03) — 2-step OTP cash-out to a linked bank or card.
    // ---------------------------------------------------------------------

    @PostMapping("/withdrawals/initiate")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "Start a wallet withdrawal (sends a one-time password)")
    public ResponseEntity<WithdrawalResultResponse> initiateWithdrawal(
            @Valid @RequestBody InitiateWithdrawalRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(walletService.initiateWithdrawal(userId, request));
    }

    @PostMapping("/withdrawals/confirm")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Audited(action = "WITHDRAWAL", resourceType = "PARTNER_WITHDRAWAL", description = "Confirmed wallet withdrawal")
    @Operation(summary = "Confirm a wallet withdrawal with the one-time password")
    public ResponseEntity<WithdrawalResultResponse> confirmWithdrawal(
            @Valid @RequestBody ConfirmWithdrawalRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(walletService.confirmWithdrawal(userId, request));
    }

    @GetMapping("/withdrawals")
    @RequiresPermission(value = {"action.redemption.redeem", "action.redemption.redeem_company"},
            logic = RequiresPermission.Logic.ANY)
    @Operation(summary = "List my wallet withdrawals (paginated, newest first)")
    public ResponseEntity<PaginatedResponse<WithdrawalHistoryResponse>> listWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int pageSize) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(walletService.listWithdrawals(userId, page, pageSize));
    }
}
