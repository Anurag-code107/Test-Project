package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardBalanceResponse;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.repository.RewardBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardBalanceServiceTest {

    @Mock
    private RewardBalanceRepository rewardBalanceRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private RewardBalanceService rewardBalanceService;

    private UUID clientId;
    private UUID userId;
    private String currencyId;
    private UUID referenceId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
        currencyId = "USD_CASH";
        referenceId = UUID.randomUUID();
    }

    @Test
    void credit_delegatesToWalletCreditInCurrentTx() {
        rewardBalanceService.credit(clientId, userId, currencyId, new BigDecimal("100.00"),
                "CLAIM", referenceId);

        verify(walletService).creditInCurrentTx(clientId, userId, currencyId,
                new BigDecimal("100.00"), "CLAIM", referenceId, null);
    }

    @Test
    void debit_delegatesToWalletReversalInCurrentTx() {
        rewardBalanceService.debit(clientId, userId, currencyId, new BigDecimal("75.00"),
                "CLAIM_REVERSAL", referenceId);

        verify(walletService).reversalInCurrentTx(clientId, userId, currencyId,
                new BigDecimal("75.00"), "CLAIM_REVERSAL", referenceId);
    }

    @Test
    void getBalances_returnsAllCurrenciesForUser() {
        RewardBalance cashBalance = RewardBalance.builder()
                .clientId(clientId).userId(userId).currencyId("USD_CASH")
                .balance(new BigDecimal("100.00")).build();
        RewardBalance pointsBalance = RewardBalance.builder()
                .clientId(clientId).userId(userId).currencyId("POINTS")
                .balance(new BigDecimal("5000")).build();

        when(rewardBalanceRepository.findByClientIdAndUserId(clientId, userId))
                .thenReturn(List.of(cashBalance, pointsBalance));

        List<RewardBalanceResponse> results = rewardBalanceService.getBalances(clientId, userId);

        assertThat(results).hasSize(2);
    }

    @Test
    void getBalances_returnsEmptyWhenNoBalances() {
        when(rewardBalanceRepository.findByClientIdAndUserId(clientId, userId))
                .thenReturn(List.of());

        List<RewardBalanceResponse> results = rewardBalanceService.getBalances(clientId, userId);

        assertThat(results).isEmpty();
    }
}
