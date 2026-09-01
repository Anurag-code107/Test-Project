package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.RewardBalanceResponse;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.repository.RewardBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardBalanceServiceTest {

    @Mock
    private RewardBalanceRepository rewardBalanceRepository;

    @InjectMocks
    private RewardBalanceService rewardBalanceService;

    private UUID clientId;
    private UUID userId;
    private String currencyId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
        currencyId = "USD_CASH";
    }

    @Test
    void credit_createsNewBalanceWhenNotExists() {
        when(rewardBalanceRepository.findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId))
                .thenReturn(Optional.empty());
        when(rewardBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rewardBalanceService.credit(clientId, userId, currencyId, new BigDecimal("100.00"));

        ArgumentCaptor<RewardBalance> captor = ArgumentCaptor.forClass(RewardBalance.class);
        verify(rewardBalanceRepository).save(captor.capture());
        RewardBalance saved = captor.getValue();

        assertThat(saved.getBalance()).isEqualByComparingTo("100.00");
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCurrencyId()).isEqualTo(currencyId);
    }

    @Test
    void credit_addsToExistingBalance() {
        RewardBalance existing = RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currencyId)
                .balance(new BigDecimal("50.00"))
                .build();
        when(rewardBalanceRepository.findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId))
                .thenReturn(Optional.of(existing));
        when(rewardBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rewardBalanceService.credit(clientId, userId, currencyId, new BigDecimal("75.50"));

        ArgumentCaptor<RewardBalance> captor = ArgumentCaptor.forClass(RewardBalance.class);
        verify(rewardBalanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void debit_subtractsFromExistingBalance() {
        RewardBalance existing = RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currencyId)
                .balance(new BigDecimal("200.00"))
                .build();
        when(rewardBalanceRepository.findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId))
                .thenReturn(Optional.of(existing));
        when(rewardBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rewardBalanceService.debit(clientId, userId, currencyId, new BigDecimal("75.00"));

        ArgumentCaptor<RewardBalance> captor = ArgumentCaptor.forClass(RewardBalance.class);
        verify(rewardBalanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("125.00");
    }

    @Test
    void debit_allowsNegativeBalance() {
        RewardBalance existing = RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currencyId)
                .balance(new BigDecimal("10.00"))
                .build();
        when(rewardBalanceRepository.findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId))
                .thenReturn(Optional.of(existing));
        when(rewardBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rewardBalanceService.debit(clientId, userId, currencyId, new BigDecimal("50.00"));

        ArgumentCaptor<RewardBalance> captor = ArgumentCaptor.forClass(RewardBalance.class);
        verify(rewardBalanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("-40.00");
    }

    @Test
    void debit_createsNewBalanceWhenNotExists() {
        when(rewardBalanceRepository.findByClientIdAndUserIdAndCurrencyIdForUpdate(clientId, userId, currencyId))
                .thenReturn(Optional.empty());
        when(rewardBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rewardBalanceService.debit(clientId, userId, currencyId, new BigDecimal("25.00"));

        ArgumentCaptor<RewardBalance> captor = ArgumentCaptor.forClass(RewardBalance.class);
        verify(rewardBalanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("-25.00");
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
