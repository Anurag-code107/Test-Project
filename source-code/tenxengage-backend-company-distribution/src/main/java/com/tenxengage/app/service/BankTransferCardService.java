package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provisions the reserved per-client <b>"Bank Transfer"</b> catalog card — the hidden vehicle for
 * the bank-transfer payout rail (category CASH, currency {@code cash}, min $1, {@code isBankTransfer=true}).
 * It is excluded from the seller browse and the client-admin catalog list, and is redeemed only via
 * the dedicated {@code POST /redemption/requests/bank-transfer} endpoint.
 *
 * <p>Idempotent get-or-create, DB-guarded by the partial unique index
 * {@code uq_catalog_bank_transfer_per_client}. Runs in its OWN transaction ({@code REQUIRES_NEW}) so a
 * concurrent first-bank-link insert race that trips the unique index rolls back <b>this</b> transaction
 * only — never poisoning the caller (e.g. {@link com.tenxengage.app.service.xtrm.XtrmBankService#addBank}),
 * which treats provisioning as non-fatal. The rare loser simply re-finds the winner's card on the next call.
 */
@Service
public class BankTransferCardService {

    private static final Logger log = LoggerFactory.getLogger(BankTransferCardService.class);

    static final String CARD_NAME = "Bank Transfer";
    static final String CARD_CURRENCY = "cash";
    static final BigDecimal CARD_MIN_AMOUNT = new BigDecimal("1.00");

    private final RedemptionCatalogItemRepository catalogItemRepository;

    public BankTransferCardService(RedemptionCatalogItemRepository catalogItemRepository) {
        this.catalogItemRepository = catalogItemRepository;
    }

    /** Returns the client's bank-transfer card, creating it if absent (idempotent). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RedemptionCatalogItem ensureBankTransferCard(UUID clientId) {
        return catalogItemRepository.findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse(clientId)
                .orElseGet(() -> {
                    RedemptionCatalogItem saved = catalogItemRepository.save(build(clientId));
                    log.info("step=bank_transfer_card_created clientId={} cardId={}", clientId, saved.getId());
                    return saved;
                });
    }

    private RedemptionCatalogItem build(UUID clientId) {
        return RedemptionCatalogItem.builder()
                .ownerClientId(clientId)
                .isBankTransfer(true)
                .category(RedemptionCategory.CASH)
                .currencyId(CARD_CURRENCY)
                .defaultMinRedemptionAmount(CARD_MIN_AMOUNT)
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .name(CARD_NAME)
                .isActive(true)
                .isReturnable(false)
                .defaultReturnWindowDays(0)
                .geographicScope(new String[0])
                .build();
    }
}
