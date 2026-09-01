package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.FundCompanyWalletRequest;
import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Funding a partner company's wallet — the API that replaces hand-written {@code INSERT}s into
 * {@code reward_wallets}.
 *
 * <p>Thin on purpose: {@link WalletService#creditCompany} already existed (with no callers) and already
 * auto-creates the wallet on first credit and guards idempotency through
 * {@code uq_ledger_credit_idempotency} on {@code (wallet, reference_type, reference_id)}. This service's job is
 * to authorise the request, turn the caller's {@code reference} into a stable UUID, and audit it.</p>
 *
 * <p><b>Deliberately not available to PARTNER_ADMIN.</b> They spend the company wallet; letting them also top it
 * up would remove the only control on how much a company can distribute.</p>
 */
@Service
public class CompanyWalletFundingService {

    private static final Logger log = LoggerFactory.getLogger(CompanyWalletFundingService.class);

    /** Reference type for a funding credit, distinct from every distribution reference. */
    public static final String REF_FUNDING = "COMPANY_WALLET_FUNDING";

    private final WalletService walletService;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final TenantValidator tenantValidator;

    public CompanyWalletFundingService(WalletService walletService,
                                        PartnerCompanyRepository partnerCompanyRepository,
                                        TenantValidator tenantValidator) {
        this.walletService = walletService;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.tenantValidator = tenantValidator;
    }

    /**
     * Credit the company's wallet, creating it if this is the first funding.
     *
     * <p>Idempotent on {@code reference}: re-submitting the same one is a no-op that returns the current
     * balances. That matters more here than anywhere else in the system — this is the only endpoint that
     * creates balance from nothing, so a double-click must not double-fund.</p>
     */
    public RewardWalletResponse fund(UUID companyId, FundCompanyWalletRequest req) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Confirm the company belongs to this tenant before crediting anything.
        partnerCompanyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", companyId));

        UUID referenceId = referenceToUuid(clientId, companyId, req.reference());

        RewardWallet wallet = walletService.creditCompany(
                clientId, companyId, req.currencyId(), req.amount(),
                REF_FUNDING, referenceId,
                req.note() == null ? "Company wallet funding: " + req.reference() : req.note());

        log.info("[step=company_wallet_funded] companyId={} currency={} amount={} reference={} walletId={}",
                companyId, req.currencyId(), req.amount(), req.reference(), wallet.getId());
        return RewardWalletResponse.from(wallet);
    }

    /**
     * Derives a stable UUID from the caller's free-text reference, so the existing ledger idempotency index
     * (which is keyed on a UUID) does the deduplication rather than a second bespoke mechanism.
     *
     * <p>Scoped by client and company as well as the reference text, so two companies can both use "PO-1"
     * without colliding. Same inputs always produce the same UUID, which is what makes the retry safe.</p>
     */
    static UUID referenceToUuid(UUID clientId, UUID companyId, String reference) {
        String seed = clientId + "|" + companyId + "|" + reference.trim();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
