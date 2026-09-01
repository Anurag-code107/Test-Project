package com.tenxengage.app.service;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.response.RewardWalletResponse;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final int MAX_RETRY = 3;

    private final RewardWalletRepository rewardWalletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final TenantValidator tenantValidator;
    private final WalletMutationDelegate delegate;

    public WalletService(RewardWalletRepository rewardWalletRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         PartnerCompanyRepository partnerCompanyRepository,
                         TenantValidator tenantValidator,
                         WalletMutationDelegate delegate) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.tenantValidator = tenantValidator;
        this.delegate = delegate;
    }

    // =====================================================================
    // Read methods (US-01)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<RewardWalletResponse> getMyWallets() {
        UUID clientId = TenantContext.getClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        return rewardWalletRepository
            .findByClientIdAndUserIdAndWalletType(clientId, userId, WalletType.INDIVIDUAL)
            .stream()
            .map(RewardWalletResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RewardWalletResponse> getCompanyWallets(UUID companyId) {
        UUID clientId = TenantContext.getClientId();
        CustomUserDetails user = tenantValidator.getCurrentUserDetails();

        if (hasRole(user, "PARTNER_SELLER")) {
            throw new AccessDeniedException("Partner sellers cannot access company wallets");
        }

        if (hasRole(user, "PARTNER_ADMIN")) {
            UUID callerCompanyId = user.getPartnerCompanyId();
            if (callerCompanyId == null) {
                throw new AccessDeniedException("Caller has no associated partner company");
            }
            if (!callerCompanyId.equals(companyId)) {
                throw new AccessDeniedException("Access denied: company mismatch");
            }
        } else {
            partnerCompanyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", companyId));
        }

        return rewardWalletRepository
            .findByClientIdAndPartnerCompanyIdAndWalletType(clientId, companyId, WalletType.COMPANY)
            .stream()
            .map(RewardWalletResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RewardWalletResponse> getUserWallets(UUID userId) {
        UUID clientId = TenantContext.getClientId();
        List<RewardWallet> wallets = rewardWalletRepository.findByClientIdAndUserId(clientId, userId);
        if (wallets.isEmpty()) {
            throw new ResourceNotFoundException("RewardWallet", "userId", userId);
        }
        return wallets.stream().map(RewardWalletResponse::from).toList();
    }

    // =====================================================================
    // Mutation methods (US-03)
    // =====================================================================

    /**
     * Credits an individual user wallet. Auto-creates the wallet on first call for a
     * (clientId, userId, currencyId) combination. Idempotent on (walletId, referenceType, referenceId).
     */
    @Audited(action = "CREATED", resourceType = "REWARD_WALLET",
             resourceId = "#result.id", description = "Wallet auto-created on first credit for currency")
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet credit(UUID clientId, UUID userId, String currencyId,
                               BigDecimal amount, String referenceType, UUID referenceId, String note) {
        return withOptimisticRetry(() ->
            delegate.doCreditInTx(clientId, userId, currencyId, amount, referenceType, referenceId, note));
    }

    /**
     * Credits an individual user wallet within the caller's existing transaction (REQUIRED).
     * Use this from @Transactional callers (e.g. grantReward) where the credit must be
     * atomic with the surrounding operation. Idempotent on (walletId, referenceType, referenceId).
     */
    @Audited(action = "CREATED", resourceType = "REWARD_WALLET",
             resourceId = "#result.id", description = "Wallet auto-created on first credit for currency")
    @Transactional(propagation = Propagation.REQUIRED)
    public RewardWallet creditInCurrentTx(UUID clientId, UUID userId, String currencyId,
                                          BigDecimal amount, String referenceType, UUID referenceId, String note) {
        rewardWalletRepository.ensureIndividualWalletExists(clientId, userId, currencyId);
        RewardWallet wallet = rewardWalletRepository
            .findForUpdate(clientId, userId, currencyId, WalletType.INDIVIDUAL)
            .orElseThrow(() -> new IllegalStateException(
                "Wallet missing after ensureExists for clientId=" + clientId
                    + " userId=" + userId + " currencyId=" + currencyId));

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

    /**
     * Credits a company wallet. Auto-creates the wallet on first call for a
     * (clientId, partnerCompanyId, currencyId) combination.
     */
    @Audited(action = "CREATED", resourceType = "REWARD_WALLET",
             resourceId = "#result.id", description = "Company wallet auto-created on first credit for currency")
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet creditCompany(UUID clientId, UUID partnerCompanyId, String currencyId,
                                      BigDecimal amount, String referenceType, UUID referenceId, String note) {
        return withOptimisticRetry(() ->
            delegate.doCreditCompanyInTx(clientId, partnerCompanyId, currencyId, amount, referenceType, referenceId, note));
    }

    /**
     * Moves amount from available to reserved. Used by F-03 redemption flow.
     */
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet reserve(UUID walletId, BigDecimal amount,
                                String referenceType, UUID referenceId) {
        return withOptimisticRetry(() -> delegate.doReserveInTx(walletId, amount, referenceType, referenceId));
    }

    /**
     * Decreases reserved balance (settlement). Used by F-03 after redemption confirmed.
     */
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet debit(UUID walletId, BigDecimal amount,
                              String referenceType, UUID referenceId) {
        return withOptimisticRetry(() -> delegate.doDebitInTx(walletId, amount, referenceType, referenceId));
    }

    /**
     * Moves amount back from reserved to available (redemption cancelled/expired).
     */
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet release(UUID walletId, BigDecimal amount,
                                String referenceType, UUID referenceId) {
        return withOptimisticRetry(() -> delegate.doReleaseInTx(walletId, amount, referenceType, referenceId));
    }

    /**
     * Returns credits to available balance (F-06 returns flow).
     */
    @Transactional(propagation = Propagation.NEVER)
    public RewardWallet returnCredit(UUID walletId, BigDecimal amount,
                                     String referenceType, UUID referenceId) {
        return withOptimisticRetry(() -> delegate.doReturnCreditInTx(walletId, amount, referenceType, referenceId));
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private <T> T withOptimisticRetry(Supplier<T> operation) {
        RuntimeException lastEx = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(50L * (1L << attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.warn("step=optimistic_lock_retry attempt={}", attempt + 1);
            }
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                lastEx = new RuntimeException("Service temporarily unavailable — please retry", e);
            } catch (DataIntegrityViolationException e) {
                if (!isUniqueViolation(e)) throw e;
                lastEx = new RuntimeException("Service temporarily unavailable — please retry", e);
            }
        }
        log.error("step=optimistic_lock_exhausted");
        if (lastEx == null) {
            throw new IllegalStateException("Retry loop interrupted before any attempt could complete");
        }
        throw lastEx;
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException se && "23505".equals(se.getSQLState())) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private boolean hasRole(CustomUserDetails user, String baseRoleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var authorities = auth != null ? auth.getAuthorities() : user.getAuthorities();
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + baseRoleName));
    }
}
