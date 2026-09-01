package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardBalanceResponse;
import com.tenxengage.app.repository.RewardBalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Deprecated
@Service
public class RewardBalanceService {

    private static final Logger log = LoggerFactory.getLogger(RewardBalanceService.class);

    private final RewardBalanceRepository rewardBalanceRepository;
    private final WalletService walletService;

    public RewardBalanceService(RewardBalanceRepository rewardBalanceRepository,
                                WalletService walletService) {
        this.rewardBalanceRepository = rewardBalanceRepository;
        this.walletService = walletService;
    }

    @Transactional
    public void credit(UUID clientId, UUID userId, String currencyId, BigDecimal amount,
                       String referenceType, UUID referenceId) {
        walletService.creditInCurrentTx(clientId, userId, currencyId, amount,
            referenceType, referenceId, null);
        log.debug("Credited {} {} to user {} via wallet ledger", amount, currencyId, userId);
    }

    @Transactional
    public void debit(UUID clientId, UUID userId, String currencyId, BigDecimal amount,
                      String referenceType, UUID referenceId) {
        walletService.reversalInCurrentTx(clientId, userId, currencyId, amount,
            referenceType, referenceId);
        log.debug("Reversed {} {} from user {} via wallet ledger", amount, currencyId, userId);
    }

    @Transactional(readOnly = true)
    public List<RewardBalanceResponse> getBalances(UUID clientId, UUID userId) {
        return rewardBalanceRepository.findByClientIdAndUserId(clientId, userId).stream()
            .map(RewardBalanceResponse::from)
            .toList();
    }
}
