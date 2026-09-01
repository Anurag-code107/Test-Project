package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.tenxengage.app.service.CompanyDistributionService.REF_DISTRIBUTION_ITEM;

/**
 * Settles one {@code WALLET_CREDIT} distribution item: company wallet debited, recipient's cash wallet
 * credited, item marked COMPLETED — <b>all in one transaction</b>.
 *
 * <p><b>Why the ledger writes are inline rather than delegated.</b> Every method on
 * {@code WalletMutationDelegate} is {@code REQUIRES_NEW}, so calling them from here would put the debit and
 * the credit in <em>separate</em> transactions — the company could be debited while the recipient's credit
 * rolled back, or vice versa. Writing both legs plus the status flip in this transaction is what makes
 * "a recipient can never be credited without the company being debited" actually true.</p>
 *
 * <p><b>Retry safety.</b> Both legs are keyed on the item id, and each checks for its own prior entry before
 * writing. So the after-commit fan-out and the stuck-item sweep can both attempt the same item — the loser
 * either finds a non-RESERVED row under the lock, or finds its ledger entry already present.</p>
 */
@Service
public class WalletCreditSettlementService {

    private static final Logger log = LoggerFactory.getLogger(WalletCreditSettlementService.class);

    private final CompanyDistributionRepository distributionRepository;
    private final CompanyDistributionItemRepository itemRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletCreditSettlementService(CompanyDistributionRepository distributionRepository,
                                          CompanyDistributionItemRepository itemRepository,
                                          RewardWalletRepository walletRepository,
                                          LedgerEntryRepository ledgerEntryRepository) {
        this.distributionRepository = distributionRepository;
        this.itemRepository = itemRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /** Outcome of an attempt, so the caller can log/count without re-reading the row. */
    public enum Outcome {
        /** Debited + credited + marked COMPLETED. */
        SETTLED,
        /** Definitively rejected; this item's share released back to available and marked FAILED. */
        FAILED,
        /** No longer RESERVED — another attempt got there first. Nothing done. */
        ALREADY_TERMINAL,
        /** Transient problem. Left RESERVED on purpose so the sweep retries; funds stay earmarked. */
        RETRY_LATER
    }

    /**
     * Settle one item. Runs in its own transaction so one recipient's failure never rolls back another's.
     *
     * <p>Definitive failures (recipient wallet cannot be resolved, currency mismatch) release only this
     * item's share. Transient failures leave the item RESERVED — never released on an unknown outcome, so
     * money is not handed back while a partial write might have landed.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome settleItem(UUID itemId) {
        CompanyDistributionItem item = itemRepository.findByIdForUpdate(itemId).orElse(null);
        if (item == null) {
            log.warn("[step=wallet_credit_settle_missing] itemId={}", itemId);
            return Outcome.ALREADY_TERMINAL;
        }
        // Guard AFTER the lock: two attempts can both read RESERVED without it.
        if (item.getStatus() != DistributionItemStatus.RESERVED) {
            return Outcome.ALREADY_TERMINAL;
        }

        CompanyDistribution header = distributionRepository.findById(item.getDistributionId()).orElse(null);
        if (header == null) {
            log.error("[step=wallet_credit_settle_orphan] itemId={} distributionId={}",
                    itemId, item.getDistributionId());
            return Outcome.RETRY_LATER;
        }

        RewardWallet company = walletRepository.findByIdForUpdate(header.getSourceWalletId()).orElse(null);
        if (company == null) {
            log.error("[step=wallet_credit_settle_no_source] itemId={}", itemId);
            return Outcome.RETRY_LATER;
        }

        // Resolve (creating on first use) the recipient's individual wallet in the same currency.
        walletRepository.ensureIndividualWalletExists(item.getClientId(), item.getRecipientUserId(),
                header.getCurrencyId());
        RewardWallet recipient = walletRepository.findForUpdate(item.getClientId(), item.getRecipientUserId(),
                header.getCurrencyId(), WalletType.INDIVIDUAL).orElse(null);
        if (recipient == null) {
            // Definitive: we cannot pay someone with no wallet and no way to make one.
            return releaseAndFail(item, company, "Could not resolve the recipient's wallet");
        }

        try {
            UUID debitId = debitCompany(company, item);
            UUID creditId = creditRecipient(recipient, item, header.getCurrencyId());

            item.setDebitLedgerEntryId(debitId);
            item.setCreditLedgerEntryId(creditId);
            item.setSettledAt(Instant.now());
            item.setStatus(DistributionItemStatus.COMPLETED);
            itemRepository.save(item);

            log.info("[step=wallet_credit_settled] itemId={} recipientUserId={} amount={}",
                    itemId, item.getRecipientUserId(), item.getAmount());
            return Outcome.SETTLED;
        } catch (BusinessRuleException definitive) {
            // e.g. reserved balance no longer covers this item — the money is not going to arrive, so give
            // this recipient's share back rather than leaving it earmarked forever.
            log.warn("[step=wallet_credit_settle_rejected] itemId={} reason={}", itemId, definitive.getMessage());
            return releaseAndFail(item, company, definitive.getMessage());
        }
    }

    /** Moves this item's share out of the company wallet's reserved balance. */
    private UUID debitCompany(RewardWallet company, CompanyDistributionItem item) {
        Optional<LedgerEntry> already = existing(company.getId(), item.getId(), LedgerEntryType.DEBIT);
        if (already.isPresent()) {
            return already.get().getId(); // retry after a partial attempt — do not debit twice
        }
        if (company.getReservedBalance().compareTo(item.getAmount()) < 0) {
            throw new BusinessRuleException("RESERVE_INSUFFICIENT",
                    "The distribution's reserved balance no longer covers this recipient.");
        }

        BigDecimal availBefore = company.getAvailableBalance();
        BigDecimal resvBefore = company.getReservedBalance();
        BigDecimal resvAfter = resvBefore.subtract(item.getAmount());

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(company.getClientId())
                .rewardWalletId(company.getId())
                .entryType(LedgerEntryType.DEBIT)
                .amount(item.getAmount())
                .currencyId(company.getCurrencyId())
                .referenceType(REF_DISTRIBUTION_ITEM)
                .referenceId(item.getId())
                .note("Wallet transfer to seller")
                .availableBalanceBefore(availBefore).availableBalanceAfter(availBefore)
                .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvAfter)
                .build());

        company.setReservedBalance(resvAfter);
        walletRepository.save(company);
        return entry.getId();
    }

    /** Adds this item's share to the recipient's available balance. */
    private UUID creditRecipient(RewardWallet recipient, CompanyDistributionItem item, String currencyId) {
        Optional<LedgerEntry> already = existing(recipient.getId(), item.getId(), LedgerEntryType.CREDIT);
        if (already.isPresent()) {
            return already.get().getId(); // retry after a partial attempt — do not credit twice
        }

        BigDecimal availBefore = recipient.getAvailableBalance();
        BigDecimal resvBefore = recipient.getReservedBalance();
        BigDecimal availAfter = availBefore.add(item.getAmount());

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(recipient.getClientId())
                .rewardWalletId(recipient.getId())
                .entryType(LedgerEntryType.CREDIT)
                .amount(item.getAmount())
                .currencyId(currencyId)
                .referenceType(REF_DISTRIBUTION_ITEM)
                .referenceId(item.getId())
                .note("Company wallet transfer")
                .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
                .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvBefore)
                .build());

        recipient.setAvailableBalance(availAfter);
        walletRepository.save(recipient);
        return entry.getId();
    }

    /** Returns this item's share to available and marks it FAILED. Only ever called for a definitive failure. */
    private Outcome releaseAndFail(CompanyDistributionItem item, RewardWallet company, String reason) {
        if (existing(company.getId(), item.getId(), LedgerEntryType.RELEASE).isEmpty()) {
            if (company.getReservedBalance().compareTo(item.getAmount()) < 0) {
                // Should not happen; releasing more than is reserved would corrupt the wallet, so stop and
                // let a human look rather than writing a nonsensical entry.
                log.error("[step=wallet_credit_release_impossible] itemId={} reserved={} amount={}",
                        item.getId(), company.getReservedBalance(), item.getAmount());
                return Outcome.RETRY_LATER;
            }
            BigDecimal availBefore = company.getAvailableBalance();
            BigDecimal resvBefore = company.getReservedBalance();

            LedgerEntry release = ledgerEntryRepository.save(LedgerEntry.builder()
                    .clientId(company.getClientId())
                    .rewardWalletId(company.getId())
                    .entryType(LedgerEntryType.RELEASE)
                    .amount(item.getAmount())
                    .currencyId(company.getCurrencyId())
                    .referenceType(REF_DISTRIBUTION_ITEM)
                    .referenceId(item.getId())
                    .note("Wallet transfer failed — share returned")
                    .availableBalanceBefore(availBefore).availableBalanceAfter(availBefore.add(item.getAmount()))
                    .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvBefore.subtract(item.getAmount()))
                    .build());

            company.setAvailableBalance(availBefore.add(item.getAmount()));
            company.setReservedBalance(resvBefore.subtract(item.getAmount()));
            walletRepository.save(company);
            item.setReleaseLedgerEntryId(release.getId());
        }

        item.setStatus(DistributionItemStatus.FAILED);
        item.setFailureReason(truncate(reason, 500));
        item.setSettledAt(Instant.now());
        itemRepository.save(item);
        log.info("[step=wallet_credit_failed] itemId={} — share released", item.getId());
        return Outcome.FAILED;
    }

    private Optional<LedgerEntry> existing(UUID walletId, UUID itemId, LedgerEntryType type) {
        return ledgerEntryRepository
                .findFirstByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                        walletId, REF_DISTRIBUTION_ITEM, itemId, type);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
