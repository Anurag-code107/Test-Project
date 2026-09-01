package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchRedemptionProcessorTest {

    @Mock private ClientRepository clientRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RedemptionOrchestrationService orchestrationService;

    @InjectMocks private BatchRedemptionProcessor processor;

    private static final UUID CLIENT_A = UUID.randomUUID();
    private static final UUID CLIENT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Self-reference needed for @Transactional(REQUIRES_NEW) proxy — unit tests bypass the proxy
        processor.setSelf(processor);
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Client client(UUID id) {
        Client c = new Client();
        c.setId(id);
        return c;
    }

    private RedemptionRequest request(UUID clientId, RedemptionCategory category) {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(clientId)
                .userId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.RESERVED)
                .processingMode(RedemptionProcessingMode.BATCH)
                .category(category)
                .scheduledBatchDate(LocalDate.now().minusDays(1))
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(UUID.randomUUID());
        return r;
    }

    private RewardWallet wallet(UUID walletId, UUID clientId) {
        return RewardWallet.builder()
                .clientId(clientId)
                .walletType(WalletType.INDIVIDUAL)
                .currencyId("cash")
                .availableBalance(new BigDecimal("0.00"))
                .reservedBalance(new BigDecimal("50.00"))
                .build();
    }

    @Test
    void processBatch_findsEligibleRows_andTransitionsToProcessing() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.NON_CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of(req));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));

        processor.processBatch();

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(r -> r.getStatus() == RedemptionStatus.PROCESSING);
    }

    @Test
    void processBatch_skipsAlreadyProcessingRows() {
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of());

        processor.processBatch();

        verify(redemptionRequestRepository, never()).save(any());
        verify(orchestrationService, never()).dispatch(any());
    }

    @Test
    void processBatch_routesCash_toOrchestrationService() {
        RedemptionRequest cashReq = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of(cashReq));
        when(redemptionRequestRepository.findByIdForUpdate(cashReq.getId())).thenReturn(Optional.of(cashReq));

        processor.processBatch();

        verify(orchestrationService).dispatch(cashReq);
    }

    @Test
    void processBatch_routesNonCash_toOrchestrationService() {
        RedemptionRequest nonCashReq = request(CLIENT_A, RedemptionCategory.NON_CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of(nonCashReq));
        when(redemptionRequestRepository.findByIdForUpdate(nonCashReq.getId())).thenReturn(Optional.of(nonCashReq));

        processor.processBatch();

        verify(orchestrationService).dispatch(nonCashReq);
    }

    @Test
    void processBatch_doesNotAffectOtherClients() {
        RedemptionRequest reqA = request(CLIENT_A, RedemptionCategory.NON_CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A), client(CLIENT_B)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of(reqA));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_B), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of());
        when(redemptionRequestRepository.findByIdForUpdate(reqA.getId())).thenReturn(Optional.of(reqA));

        processor.processBatch();

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(r -> r.getClientId().equals(CLIENT_A));
    }

    @Test
    void processBatch_noEligibleRows_exitsCleanly() {
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(CLIENT_A), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(List.of());

        processor.processBatch();

        verify(orchestrationService, never()).dispatch(any());
    }

    @Test
    void dispatchItem_onDispatchFailure_releasesWalletFundsAndSetsFailed() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.NON_CASH);
        RewardWallet w = wallet(req.getWalletId(), CLIENT_A);
        BigDecimal originalReserved = w.getReservedBalance();
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(walletRepository.findByIdForUpdate(req.getWalletId())).thenReturn(Optional.of(w));
        doThrow(new RuntimeException("vendor down")).when(orchestrationService).dispatch(any());

        processor.dispatchItem(req.getId());

        ArgumentCaptor<RedemptionRequest> reqCaptor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(reqCaptor.capture());
        assertThat(reqCaptor.getAllValues()).anyMatch(r -> r.getStatus() == RedemptionStatus.FAILED);

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getEntryType()).isEqualTo(com.tenxengage.app.entity.enums.LedgerEntryType.RELEASE);

        ArgumentCaptor<RewardWallet> walletCaptor = ArgumentCaptor.forClass(RewardWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getReservedBalance())
                .isEqualByComparingTo(originalReserved.subtract(req.getAmount()));
    }

    @Test
    void dispatchItem_skipsNonReservedRequest() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        req.setStatus(RedemptionStatus.PROCESSING);
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));

        processor.dispatchItem(req.getId());

        verify(orchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository, never()).save(any());
    }
}
