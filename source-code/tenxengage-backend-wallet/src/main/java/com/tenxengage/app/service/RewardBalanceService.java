package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardBalanceResponse;
import com.tenxengage.app.entity.RewardBalance;
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

    public RewardBalanceService(RewardBalanceRepository rewardBalanceRepository) {
        this.rewardBalanceRepository = rewardBalanceRepository;
    }

    @Transactional
    public void credit(UUID clientId, UUID userId, String currencyId, BigDecimal amount) {
        RewardBalance balance = rewardBalanceRepository
            .findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId)
            .orElseGet(() -> RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currencyId)
                .balance(BigDecimal.ZERO)
                .build());
        balance.setBalance(balance.getBalance().add(amount));
        rewardBalanceRepository.save(balance);
        log.debug("Credited {} {} to user {} (new balance: {})", amount, currencyId, userId, balance.getBalance());
    }

    @Transactional
    public void debit(UUID clientId, UUID userId, String currencyId, BigDecimal amount) {
        RewardBalance balance = rewardBalanceRepository
            .findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId)
            .orElseGet(() -> RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currencyId)
                .balance(BigDecimal.ZERO)
                .build());
        balance.setBalance(balance.getBalance().subtract(amount));
        rewardBalanceRepository.save(balance);
        log.debug("Debited {} {} from user {} (new balance: {})", amount, currencyId, userId, balance.getBalance());
    }

    @Transactional(readOnly = true)
    public List<RewardBalanceResponse> getBalances(UUID clientId, UUID userId) {
        return rewardBalanceRepository.findByClientIdAndUserId(clientId, userId).stream()
            .map(RewardBalanceResponse::from)
            .toList();
    }
}
