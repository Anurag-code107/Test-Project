package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.dto.request.xtrm.LinkBankAccountRequest;
import com.tenxengage.app.dto.response.xtrm.LinkedBankResponse;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.BankTransferCardService;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.DeleteBankResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkBankCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.LinkBankResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Manages the current user's XTRM bank/ACH links and payout-method preference (F-03 multi-bank enhancement).
 *
 * <p>A user may link <b>many</b> banks. Each is persisted as a {@link PartnerLinkedBank} row so the Payout
 * tab lists them with a fast DB read — no XTRM {@code GetLinkedBankAccounts} round-trip per view. XTRM still
 * owns the beneficiary: {@link #addBank} calls {@code LinkBankBeneficiary} and {@link #removeBank} calls
 * {@code DeleteBankBeneficiary}, keeping the local table in sync. The <b>default</b> bank stays on
 * {@code partner_redemption.partner_linked_bank_id} (a single value → one default free), so the payout path
 * is unchanged.</p>
 *
 * <p>Stores only the XTRM reference id + a masked display label — the raw account / routing numbers from
 * {@link LinkBankAccountRequest} are pass-through to XTRM and never persisted or logged. All operations are
 * self-only (the controller resolves the current user from the JWT) and tenant-scoped by {@code clientId}.
 * XTRM calls run outside a transaction; the result is persisted afterward.</p>
 */
@Service
public class XtrmBankService {

    private static final Logger log = LoggerFactory.getLogger(XtrmBankService.class);
    private static final int LABEL_MAX = 100;

    private final PartnerRedemptionRepository userRedemptionRepository;
    private final PartnerLinkedBankRepository linkedBankRepository;
    private final XtrmApiClient xtrmApiClient;
    private final XtrmEnrollmentService enrollmentService;
    private final BankTransferCardService bankTransferCardService;

    public XtrmBankService(PartnerRedemptionRepository userRedemptionRepository,
                           PartnerLinkedBankRepository linkedBankRepository,
                           XtrmApiClient xtrmApiClient,
                           XtrmEnrollmentService enrollmentService,
                           BankTransferCardService bankTransferCardService) {
        this.userRedemptionRepository = userRedemptionRepository;
        this.linkedBankRepository = linkedBankRepository;
        this.xtrmApiClient = xtrmApiClient;
        this.enrollmentService = enrollmentService;
        this.bankTransferCardService = bankTransferCardService;
    }

    /**
     * Add a bank/ACH beneficiary for the user (append — never replaces an existing one). Requires the user
     * to be XTRM-enrolled (a PAT is needed) — lazily enrolls first, throwing {@code XTRM_NOT_ENROLLED} (422)
     * otherwise. Links at XTRM, inserts a local {@link PartnerLinkedBank} row, and makes it the default only
     * if the user has none yet. XTRM domain rejections (duplicate bank, invalid routing) surface as a 422
     * with an errorCode; transient outages as a 503. The raw XTRM message is never echoed.
     */
    public PartnerRedemption addBank(UUID userId, LinkBankAccountRequest request) {
        // Guarantees an ENROLLED profile with a PAT, or throws XTRM_NOT_ENROLLED (422).
        String recipientUserId = enrollmentService.ensureEnrolledForPayout(userId);
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);

        LinkBankResult result = xtrmApiClient.linkBankBeneficiary(new LinkBankCommand(
                recipientUserId,
                request.contactName(), request.contactPhone(), request.accountNumber(), request.routingNumber(),
                request.swiftBic(), request.institutionName(),
                request.addressLine1(), request.addressLine2(), request.city(),
                request.region(), request.postalCode(), request.countryIso2(),
                request.withdrawType()));

        if (!result.success()) {
            // Transient outage (XTRM unreachable) → 503 retry-later, not a 422 "check your details".
            if (result.retryable()) {
                throw new ExternalServiceException("XTRM_UNAVAILABLE",
                        "Payouts are temporarily unavailable. Please try again shortly.");
            }
            throw new BusinessRuleException(result.errorCode(), friendlyBankError(result.errorCode()));
        }

        String beneficiaryId = result.partnerLinkedBankId();
        String label = maskLabel(request);

        // Persist the local row — the bank list reads from here (no XTRM round-trip per view). masked_label,
        // currency, country, and withdraw type come from the add-form input (the Link response has no masked
        // account/bank name). v1 = USD/US; withdraw type + country stored for forward-compat.
        try {
            linkedBankRepository.save(PartnerLinkedBank.builder()
                    .clientId(profile.getClientId())
                    .userId(userId)
                    .xtrmBeneficiaryId(beneficiaryId)
                    .maskedLabel(label)
                    .currency("USD")
                    .countryIso2(request.countryIso2())
                    .withdrawType(request.withdrawType())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            // Belt-and-suspenders: XTRM normally rejects a duplicate first (XTRM_BANK_DUPLICATE).
            throw new BusinessRuleException("XTRM_BANK_DUPLICATE", friendlyBankError("XTRM_BANK_DUPLICATE"));
        }

        // Append-not-replace: the first bank becomes the default; an existing default is never overwritten.
        if (isBlank(profile.getPartnerLinkedBankId())) {
            profile.setPartnerLinkedBankId(beneficiaryId);
            profile.setLinkedBankLabel(label);
            profile = userRedemptionRepository.save(profile);
        }

        // Provision the client's hidden bank-transfer card on first bank link (idempotent, own
        // REQUIRES_NEW transaction). NON-FATAL: a failure must never fail the bank link — the
        // bank-transfer redeem path re-ensures the card as a safety net.
        try {
            bankTransferCardService.ensureBankTransferCard(profile.getClientId());
        } catch (Exception e) {
            log.warn("[step=bank_transfer_card_provision_failed] clientId={} — non-fatal", profile.getClientId(), e);
        }

        log.info("[step=xtrm_bank_linked] userId={}", userId); // no account/routing number
        return profile;
    }

    /**
     * List the user's linked banks (fast local read — no XTRM call). Marks the default bank by matching the
     * pointer on {@code partner_redemption}. Read-only: never writes a profile shell on this GET.
     */
    public List<LinkedBankResponse> listBanks(UUID userId) {
        PartnerRedemption profile = enrollmentService.getProfileView(userId);
        UUID clientId = profile.getClientId();
        String defaultBeneficiaryId = profile.getPartnerLinkedBankId();
        return linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(userId, clientId).stream()
                .map(bank -> LinkedBankResponse.of(bank,
                        bank.getXtrmBeneficiaryId().equals(defaultBeneficiaryId)))
                .toList();
    }

    /**
     * Remove a specific linked bank (by our row PK). Hard-deletes it at XTRM ({@code DeleteBankBeneficiary})
     * then soft-deletes the local row. A transient XTRM failure keeps the row + surfaces a 503 (so the local
     * table and XTRM don't drift); a non-retryable rejection (e.g. already gone at XTRM) is idempotent — we
     * still soft-delete locally. If the removed bank was the default: auto-promote the oldest remaining bank,
     * or (none left) clear the default and reset a BANK rail to ANYPAY so the user isn't stranded.
     */
    public PartnerRedemption removeBank(UUID userId, UUID bankId) {
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);
        UUID clientId = profile.getClientId();

        PartnerLinkedBank bank = linkedBankRepository.findByIdAndUserIdAndClientId(bankId, userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedBank", "id", bankId));

        DeleteBankResult result = xtrmApiClient.deleteBankBeneficiary(
                new DeleteBankCommand(profile.getRecipientUserId(), bank.getXtrmBeneficiaryId()));
        if (!result.success() && result.retryable()) {
            throw new ExternalServiceException("XTRM_UNAVAILABLE",
                    "Payouts are temporarily unavailable. Please try again shortly.");
        }
        if (!result.success()) {
            // Non-retryable rejection → treat as already-removed at XTRM; proceed to soft-delete locally.
            log.warn("[step=xtrm_bank_unlink] userId={} XTRM delete non-fatal, soft-deleting locally", userId);
        }

        bank.setDeleted(true);
        linkedBankRepository.save(bank);

        // Re-point the default if we just removed it (has banks <=> has default).
        if (bank.getXtrmBeneficiaryId().equals(profile.getPartnerLinkedBankId())) {
            List<PartnerLinkedBank> remaining =
                    linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(userId, clientId);
            if (!remaining.isEmpty()) {
                PartnerLinkedBank promoted = remaining.get(0); // oldest — deterministic
                profile.setPartnerLinkedBankId(promoted.getXtrmBeneficiaryId());
                profile.setLinkedBankLabel(promoted.getMaskedLabel());
            } else {
                profile.setPartnerLinkedBankId(null);
                profile.setLinkedBankLabel(null);
                if (profile.getPayoutMethod() == RedemptionPayoutMethod.BANK) {
                    profile.setPayoutMethod(RedemptionPayoutMethod.ANYPAY);
                }
            }
            profile = userRedemptionRepository.save(profile);
        }
        log.info("[step=xtrm_bank_unlinked] userId={}", userId);
        return profile;
    }

    /**
     * Set the default bank (by our row PK) — the destination for the BANK rail. No XTRM call: the default is
     * a pointer on {@code partner_redemption}. The payout path already reads it, so payouts are unchanged.
     */
    public PartnerRedemption setDefaultBank(UUID userId, UUID bankId) {
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);
        PartnerLinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndClientId(bankId, userId, profile.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("LinkedBank", "id", bankId));
        profile.setPartnerLinkedBankId(bank.getXtrmBeneficiaryId());
        profile.setLinkedBankLabel(bank.getMaskedLabel());
        PartnerRedemption saved = userRedemptionRepository.save(profile);
        log.info("[step=xtrm_default_bank_set] userId={}", userId);
        return saved;
    }

    /**
     * Set the payout rail. Selecting {@code BANK} without a linked bank is rejected with
     * {@code BANK_NOT_LINKED} (422) — the FE prompts the user to link one first.
     */
    public PartnerRedemption setPayoutMethod(UUID userId, RedemptionPayoutMethod method) {
        PartnerRedemption profile = enrollmentService.getOrCreateProfile(userId);
        if (method == RedemptionPayoutMethod.BANK && isBlank(profile.getPartnerLinkedBankId())) {
            throw new BusinessRuleException("BANK_NOT_LINKED",
                    "Link a bank account before selecting the bank payout method.");
        }
        if (method == RedemptionPayoutMethod.CARD && isBlank(profile.getPartnerLinkedCardId())) {
            throw new BusinessRuleException("CARD_NOT_LINKED",
                    "Link a card before selecting the card payout method.");
        }
        profile.setPayoutMethod(method);
        PartnerRedemption saved = userRedemptionRepository.save(profile);
        log.info("[step=xtrm_payout_method_set] userId={} method={}", userId, method);
        return saved;
    }

    // ---------------------------------------------------------------------

    private static String friendlyBankError(String errorCode) {
        if ("XTRM_BANK_DUPLICATE".equals(errorCode)) {
            return "This bank account is already linked.";
        }
        return "We couldn't link that bank account. Please check the details and try again.";
    }

    /** Build a masked, display-only label like {@code "Wells Fargo ••1898"}; never contains the full number. */
    private static String maskLabel(LinkBankAccountRequest request) {
        String institution = isBlank(request.institutionName()) ? "Bank" : request.institutionName().trim();
        String account = request.accountNumber();
        String last4 = (account != null && account.length() >= 4)
                ? account.substring(account.length() - 4) : "";
        String label = last4.isEmpty() ? institution : institution + " ••" + last4;
        return label.length() > LABEL_MAX ? label.substring(0, LABEL_MAX) : label;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
