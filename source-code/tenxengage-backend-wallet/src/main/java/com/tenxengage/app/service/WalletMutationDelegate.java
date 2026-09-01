package com.tenxengage.app.service;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Holds all wallet mutation methods that require REQUIRES_NEW transaction propagation.
 * Extracted from WalletService to avoid the @Lazy @Autowired self-proxy pattern.
 * WalletService delegates to this bean for all isolated mutation transactions.
 */
@Service
public class WalletMutationDelegate {

    private static final Logger log = LoggerFactory.getLogger(WalletMutationDelegate.class);

    private final RewardWalletRepository rewardWalletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletMutationDelegate(RewardWalletRepository rewardWalletRepository,
                                   LedgerEntryRepository ledgerEntryRepository) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doCreditInTx(UUID clientId, UUID userId, String currencyId,
                                     BigDecimal amount, String referenceType, UUID referenceId, String note) {
        RewardWallet wallet = rewardWalletRepository
            .findForUpdate(clientId, userId, currencyId, WalletType.INDIVIDUAL)
            .orElseGet(() -> rewardWalletRepository.save(
                RewardWallet.builder()
                    .clientId(clientId).userId(userId)
                    .currencyId(currencyId).walletType(WalletType.INDIVIDUAL)
                    .build()));

        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_credit_skipped walletId={} referenceType={} referenceId={}",
                wallet.getId(), referenceType, referenceId);
            return wallet;
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal availAfter  = availBefore.add(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(clientId)
            .rewardWalletId(wallet.getId())
            .entryType(LedgerEntryType.CREDIT)
            .amount(amount)
            .currencyId(currencyId)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .note(note)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvBefore)
            .build());

        wallet.setAvailableBalance(availAfter);
        RewardWallet saved = rewardWalletRepository.save(wallet);
        log.info("step=balance_credited walletId={} amount={} currencyId={}",
            saved.getId(), amount, currencyId);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doCreditCompanyInTx(UUID clientId, UUID partnerCompanyId, String currencyId,
                                            BigDecimal amount, String referenceType, UUID referenceId, String note) {
        RewardWallet wallet = rewardWalletRepository
            .findForUpdateByCompany(clientId, partnerCompanyId, currencyId, WalletType.COMPANY)
            .orElseGet(() -> rewardWalletRepository.save(
                RewardWallet.builder()
                    .clientId(clientId).partnerCompanyId(partnerCompanyId)
                    .currencyId(currencyId).walletType(WalletType.COMPANY)
                    .build()));

        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_credit_skipped walletId={}", wallet.getId());
            return wallet;
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal availAfter  = availBefore.add(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(clientId)
            .rewardWalletId(wallet.getId())
            .entryType(LedgerEntryType.CREDIT)
            .amount(amount)
            .currencyId(currencyId)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .note(note)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvBefore)
            .build());

        wallet.setAvailableBalance(availAfter);
        return rewardWalletRepository.save(wallet);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doReserveInTx(UUID walletId, BigDecimal amount,
                                      String referenceType, UUID referenceId) {
        RewardWallet wallet = findWallet(walletId);
        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_reserve_skipped walletId={} referenceType={} referenceId={}",
                wallet.getId(), referenceType, referenceId);
            return wallet;
        }
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException(
                "Insufficient available balance for " + wallet.getCurrencyId());
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal availAfter  = availBefore.subtract(amount);
        BigDecimal resvAfter   = resvBefore.add(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(wallet.getClientId())
            .rewardWalletId(walletId)
            .entryType(LedgerEntryType.RESERVE)
            .amount(amount)
            .currencyId(wallet.getCurrencyId())
            .referenceType(referenceType)
            .referenceId(referenceId)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvAfter)
            .build());

        wallet.setAvailableBalance(availAfter);
        wallet.setReservedBalance(resvAfter);
        log.info("step=balance_reserved walletId={} amount={}", walletId, amount);
        return rewardWalletRepository.save(wallet);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doDebitInTx(UUID walletId, BigDecimal amount,
                                    String referenceType, UUID referenceId) {
        RewardWallet wallet = findWallet(walletId);
        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_debit_skipped walletId={} referenceType={} referenceId={}",
                wallet.getId(), referenceType, referenceId);
            return wallet;
        }
        if (wallet.getReservedBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException("Reserved balance insufficient for this operation");
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal resvAfter   = resvBefore.subtract(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(wallet.getClientId())
            .rewardWalletId(walletId)
            .entryType(LedgerEntryType.DEBIT)
            .amount(amount)
            .currencyId(wallet.getCurrencyId())
            .referenceType(referenceType)
            .referenceId(referenceId)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availBefore)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvAfter)
            .build());

        wallet.setReservedBalance(resvAfter);
        return rewardWalletRepository.save(wallet);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doReleaseInTx(UUID walletId, BigDecimal amount,
                                      String referenceType, UUID referenceId) {
        RewardWallet wallet = findWallet(walletId);
        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_release_skipped walletId={} referenceType={} referenceId={}",
                wallet.getId(), referenceType, referenceId);
            return wallet;
        }
        if (wallet.getReservedBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException("Reserved balance insufficient for this operation");
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal availAfter  = availBefore.add(amount);
        BigDecimal resvAfter   = resvBefore.subtract(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(wallet.getClientId())
            .rewardWalletId(walletId)
            .entryType(LedgerEntryType.RELEASE)
            .amount(amount)
            .currencyId(wallet.getCurrencyId())
            .referenceType(referenceType)
            .referenceId(referenceId)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvAfter)
            .build());

        wallet.setAvailableBalance(availAfter);
        wallet.setReservedBalance(resvAfter);
        return rewardWalletRepository.save(wallet);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardWallet doReturnCreditInTx(UUID walletId, BigDecimal amount,
                                           String referenceType, UUID referenceId) {
        RewardWallet wallet = findWallet(walletId);
        if (referenceId != null && ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceId(
                    wallet.getId(), referenceType, referenceId)) {
            log.info("step=duplicate_return_credit_skipped walletId={} referenceType={} referenceId={}",
                wallet.getId(), referenceType, referenceId);
            return wallet;
        }

        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore  = wallet.getReservedBalance();
        BigDecimal availAfter  = availBefore.add(amount);

        ledgerEntryRepository.save(LedgerEntry.builder()
            .clientId(wallet.getClientId())
            .rewardWalletId(walletId)
            .entryType(LedgerEntryType.RETURN_CREDIT)
            .amount(amount)
            .currencyId(wallet.getCurrencyId())
            .referenceType(referenceType)
            .referenceId(referenceId)
            .availableBalanceBefore(availBefore).availableBalanceAfter(availAfter)
            .reservedBalanceBefore(resvBefore).reservedBalanceAfter(resvBefore)
            .build());

        wallet.setAvailableBalance(availAfter);
        return rewardWalletRepository.save(wallet);
    }

    private RewardWallet findWallet(UUID walletId) {
        return rewardWalletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", walletId));
    }
}
