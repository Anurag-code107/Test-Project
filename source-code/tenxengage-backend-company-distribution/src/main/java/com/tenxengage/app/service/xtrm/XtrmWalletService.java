package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.ConfirmWithdrawalRequest;
import com.tenxengage.app.dto.request.xtrm.InitiateWithdrawalRequest;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.xtrm.DigitalWalletResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalHistoryResponse;
import com.tenxengage.app.dto.response.xtrm.WithdrawalResultResponse;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.xtrm.PartnerLinkedCard;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.entity.xtrm.PartnerWithdrawal;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedCardRepository;
import com.tenxengage.app.repository.xtrm.PartnerWithdrawalRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UserWithdrawCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.UserWithdrawResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A user's XTRM digital wallet: read-only wallet listing (F-03 digital-wallet enhancement) plus wallet
 * cash-out ({@code UserWithdrawFund}, 2-step OTP) to a linked bank or card (F-03 withdrawal enhancement).
 * Keys on the payee's PAT; requires XTRM enrollment. XTRM calls run outside any transaction.
 *
 * <p>Withdrawal is 2-step: {@link #initiateWithdrawal} sends the amount/destination (XTRM replies "OTP
 * sent"); {@link #confirmWithdrawal} resends them + the OTP and, on success, writes a {@code partner_withdrawal}
 * history row. The user's XTRM wallet is XTRM-side — withdrawal does NOT touch our reward ledger.</p>
 */
@Service
public class XtrmWalletService {

    private static final Logger log = LoggerFactory.getLogger(XtrmWalletService.class);
    private static final String DEFAULT_CURRENCY = "USD";
    private static final int MAX_HISTORY_PAGE_SIZE = 50;

    /** Bank / ACH rail (XTR94500) — withdraw to a linked bank. */
    @Value("${redemption.xtrm.bank-payment-method-id:XTR94500}")
    private String bankPaymentMethodId;

    /** Rapid Transfer / card rail (XTR94508) — withdraw to a linked card (ACH). */
    @Value("${redemption.xtrm.rapid-transfer-payment-method-id:XTR94508}")
    private String rapidTransferPaymentMethodId;

    private final XtrmApiClient xtrmApiClient;
    private final XtrmEnrollmentService enrollmentService;
    private final PartnerLinkedBankRepository linkedBankRepository;
    private final PartnerLinkedCardRepository linkedCardRepository;
    private final PartnerWithdrawalRepository withdrawalRepository;

    public XtrmWalletService(XtrmApiClient xtrmApiClient,
                             XtrmEnrollmentService enrollmentService,
                             PartnerLinkedBankRepository linkedBankRepository,
                             PartnerLinkedCardRepository linkedCardRepository,
                             PartnerWithdrawalRepository withdrawalRepository) {
        this.xtrmApiClient = xtrmApiClient;
        this.enrollmentService = enrollmentService;
        this.linkedBankRepository = linkedBankRepository;
        this.linkedCardRepository = linkedCardRepository;
        this.withdrawalRepository = withdrawalRepository;
    }

    /**
     * List the current user's XTRM wallets (every currency). Requires enrollment (a PAT) — throws
     * {@code XTRM_NOT_ENROLLED} (422) otherwise. Read-only: never writes a profile shell on this GET.
     */
    public List<DigitalWalletResponse> listWallets(UUID userId) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        String pat = requireEnrolled(profile);

        GetWalletsResult result = xtrmApiClient.getBeneficiaryWallets(new GetWalletsCommand(pat));
        if (!result.success()) {
            if (result.retryable()) {
                throw new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly.");
            }
            throw new BusinessRuleException("XTRM_WALLETS_FAILED",
                    "We couldn't load your wallets. Please try again.");
        }
        log.info("[step=xtrm_wallets] userId={} count={}", userId, result.wallets().size());
        return result.wallets().stream().map(DigitalWalletResponse::of).toList();
    }

    /**
     * Step 1 of a withdrawal: send amount + destination WITHOUT an OTP. XTRM validates and emails/SMSes a
     * one-time password; the response carries no transaction id. Returns {@code otpRequired=true}.
     */
    public WithdrawalResultResponse initiateWithdrawal(UUID userId, InitiateWithdrawalRequest request) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        String pat = requireEnrolled(profile);
        ResolvedDestination dest = resolveDestination(userId, profile.getClientId(),
                request.destinationType(), request.destinationId());

        UserWithdrawResult result = xtrmApiClient.userWithdrawFund(withdrawCommand(pat, request.amount(), dest, null));
        checkFailure(result);
        if (!result.otpRequired()) {
            // Defensive: XTRM completed without an OTP round-trip. Persist + return the completed result.
            log.warn("[step=xtrm_withdraw] userId={} completed on initiate (no OTP step)", userId);
            return WithdrawalResultResponse.of(persist(profile, request.amount(), dest, result));
        }
        log.info("[step=xtrm_withdraw_initiated] userId={} destType={}", userId, request.destinationType());
        return WithdrawalResultResponse.otpSent();
    }

    /**
     * Step 2 of a withdrawal: resend amount + destination WITH the OTP. On success the transfer executes; we
     * write a {@code partner_withdrawal} history row and return the executed amounts. A still-pending OTP
     * (wrong/expired code) surfaces as {@code XTRM_WITHDRAW_OTP_INVALID} (422).
     */
    public WithdrawalResultResponse confirmWithdrawal(UUID userId, ConfirmWithdrawalRequest request) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        String pat = requireEnrolled(profile);
        ResolvedDestination dest = resolveDestination(userId, profile.getClientId(),
                request.destinationType(), request.destinationId());

        UserWithdrawResult result = xtrmApiClient.userWithdrawFund(
                withdrawCommand(pat, request.amount(), dest, request.otp()));
        checkFailure(result);
        if (result.otpRequired()) {
            // Confirm returned "OTP sent" again → the submitted code was not accepted.
            throw new BusinessRuleException("XTRM_WITHDRAW_OTP_INVALID",
                    "That code wasn't accepted. Please request a new code and try again.");
        }
        PartnerWithdrawal saved = persist(profile, request.amount(), dest, result);
        log.info("[step=xtrm_withdraw_completed] userId={} txnId={}", userId, saved.getXtrmPaymentTransactionId());
        return WithdrawalResultResponse.of(saved);
    }

    /** Paginated withdrawal history for the user (newest first). {@code size} is clamped to
     *  {@value #MAX_HISTORY_PAGE_SIZE}; pages over the full history so the UI can navigate all rows. */
    public PaginatedResponse<WithdrawalHistoryResponse> listWithdrawals(UUID userId, int page, int pageSize) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_HISTORY_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<WithdrawalHistoryResponse> result = withdrawalRepository
                .findByClientIdAndUserIdOrderByCreatedAtDesc(profile.getClientId(), userId, PageRequest.of(safePage, safeSize))
                .map(WithdrawalHistoryResponse::of);
        return PaginatedResponse.from(result);
    }

    // ---------------------------------------------------------------------

    /** A withdrawal destination resolved from our row PK to the XTRM refs + display label. */
    private record ResolvedDestination(
            String type, String paymentMethodId, String userLinkedBankId, String cardToken,
            String bankPaymentMethod, String label, UUID ref) {
    }

    private ResolvedDestination resolveDestination(UUID userId, UUID clientId, String type, UUID destinationId) {
        if ("CARD".equals(type)) {
            PartnerLinkedCard card = linkedCardRepository
                    .findByIdAndUserIdAndClientId(destinationId, userId, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("LinkedCard", "id", destinationId));
            return new ResolvedDestination("CARD", rapidTransferPaymentMethodId, null, card.getCardToken(),
                    "ACH", cardLabel(card), card.getId());
        }
        PartnerLinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndClientId(destinationId, userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedBank", "id", destinationId));
        return new ResolvedDestination("BANK", bankPaymentMethodId, bank.getXtrmBeneficiaryId(), null,
                null, bank.getMaskedLabel(), bank.getId());
    }

    private UserWithdrawCommand withdrawCommand(String pat, BigDecimal amount, ResolvedDestination dest, String otp) {
        return new UserWithdrawCommand(pat, amount, DEFAULT_CURRENCY, dest.paymentMethodId(),
                dest.userLinkedBankId(), dest.cardToken(), dest.bankPaymentMethod(), otp, "Withdraw Funds");
    }

    private PartnerWithdrawal persist(PartnerRedemption profile, BigDecimal requestedAmount,
                                      ResolvedDestination dest, UserWithdrawResult result) {
        BigDecimal gross = result.totalGross() != null ? result.totalGross() : requestedAmount;
        BigDecimal fee = result.fee() != null ? result.fee() : BigDecimal.ZERO;
        BigDecimal net = result.amountNet() != null ? result.amountNet() : gross.subtract(fee);
        String currency = result.currency() != null ? result.currency() : DEFAULT_CURRENCY;
        return withdrawalRepository.save(PartnerWithdrawal.builder()
                .clientId(profile.getClientId())
                .userId(profile.getUserId())
                .amountGross(gross)
                .fee(fee)
                .amountNet(net)
                .currency(currency)
                .destinationType(dest.type())
                .destinationLabel(dest.label())
                .destinationRef(dest.ref())
                .xtrmPaymentTransactionId(result.paymentTransactionId())
                .status("COMPLETED")
                .build());
    }

    /** Map an XTRM withdrawal failure to a 503 (transient) or 422 (definitive). */
    private static void checkFailure(UserWithdrawResult result) {
        if (result.success()) {
            return;
        }
        if (result.retryable()) {
            throw new ExternalServiceException("XTRM_UNAVAILABLE",
                    "Payouts are temporarily unavailable. Please try again shortly.");
        }
        throw new BusinessRuleException("XTRM_WITHDRAW_FAILED",
                "We couldn't process that withdrawal. Please try again.");
    }

    private static String requireEnrolled(PartnerRedemption profile) {
        String pat = profile.getRecipientUserId();
        if (isBlank(pat)) {
            throw new BusinessRuleException("XTRM_NOT_ENROLLED",
                    "This account isn't set up for payouts yet. Complete your payout profile first.");
        }
        return pat;
    }

    private static String cardLabel(PartnerLinkedCard card) {
        String type = isBlank(card.getCardType()) ? "Card" : card.getCardType();
        String last4 = card.getMaskedLast4();
        return isBlank(last4) ? type : type + " ••" + last4;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
