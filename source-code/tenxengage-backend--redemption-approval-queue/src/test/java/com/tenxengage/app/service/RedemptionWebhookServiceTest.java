package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.event.RedemptionCompletedEvent;
import com.tenxengage.app.event.RedemptionFailedEvent;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedemptionWebhookServiceTest {

    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RedemptionWebhookEventRepository webhookEventRepository;
    @Mock private RedemptionEventProducer redemptionEventProducer;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private RedemptionWebhookService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private RedemptionRequest request(RedemptionStatus status) {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(UUID.randomUUID())
                .walletId(WALLET_ID)
                .catalogItemId(UUID.randomUUID())
                .amount(AMOUNT)
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(status)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(UUID.randomUUID());
        r.setVendorReferenceId("VENDOR-TX-" + UUID.randomUUID());
        r.setDispatchAttemptedAt(Instant.now());
        return r;
    }

    private RewardWallet wallet() {
        RewardWallet w = RewardWallet.builder()
                .clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL)
                .userId(UUID.randomUUID())
                .currencyId("cash")
                .availableBalance(new BigDecimal("200.00"))
                .reservedBalance(AMOUNT)
                .build();
        w.setId(WALLET_ID);
        return w;
    }

    private RedemptionWebhookEvent webhookEvent(UUID redemptionRequestId) {
        RedemptionWebhookEvent e = RedemptionWebhookEvent.builder()
                .clientId(CLIENT_ID)
                .vendor("xtrm")
                .redemptionRequestId(redemptionRequestId)
                .idempotencyKey(UUID.randomUUID().toString())
                .payload("{}")
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build();
        e.setId(UUID.randomUUID());
        return e;
    }

    @Test
    void process_completionEvent_writesDebitAndCompletes() {
        RedemptionRequest req = request(RedemptionStatus.PROCESSING);
        RedemptionWebhookEvent event = webhookEvent(req.getId());
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet()));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req.getId(), event, true, null);

        ArgumentCaptor<RedemptionRequest> reqCaptor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo(RedemptionStatus.COMPLETED);
        assertThat(reqCaptor.getValue().getCompletedAt()).isNotNull();

        ArgumentCaptor<RedemptionWebhookEvent> evtCaptor = ArgumentCaptor.forClass(RedemptionWebhookEvent.class);
        verify(webhookEventRepository).save(evtCaptor.capture());
        assertThat(evtCaptor.getValue().getStatus()).isEqualTo(WebhookStatus.PROCESSED);

        verify(ledgerEntryRepository).save(any());
        verify(eventPublisher).publishEvent(any(RedemptionCompletedEvent.class));
    }

    @Test
    void process_failureEvent_writesReleaseAndFails() {
        RedemptionRequest req = request(RedemptionStatus.PROCESSING);
        RedemptionWebhookEvent event = webhookEvent(req.getId());
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet()));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req.getId(), event, false, "Vendor rejected transfer");

        ArgumentCaptor<RedemptionRequest> reqCaptor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo(RedemptionStatus.FAILED);
        assertThat(reqCaptor.getValue().getFailureReason()).isEqualTo("Vendor rejected transfer");

        ArgumentCaptor<RedemptionWebhookEvent> evtCaptor = ArgumentCaptor.forClass(RedemptionWebhookEvent.class);
        verify(webhookEventRepository).save(evtCaptor.capture());
        assertThat(evtCaptor.getValue().getStatus()).isEqualTo(WebhookStatus.PROCESSED);

        verify(ledgerEntryRepository).save(any());
        verify(eventPublisher).publishEvent(any(RedemptionFailedEvent.class));
    }

    @Test
    void process_alreadyCompleted_deadLetters() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED);
        RedemptionWebhookEvent event = webhookEvent(req.getId());
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));

        service.process(req.getId(), event, true, null);

        ArgumentCaptor<RedemptionWebhookEvent> evtCaptor = ArgumentCaptor.forClass(RedemptionWebhookEvent.class);
        verify(webhookEventRepository).save(evtCaptor.capture());
        assertThat(evtCaptor.getValue().getStatus()).isEqualTo(WebhookStatus.DEAD_LETTERED);

        verify(ledgerEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void process_kafkaEventPublished_afterCommit() {
        RedemptionRequest req = request(RedemptionStatus.PROCESSING);
        RedemptionWebhookEvent event = webhookEvent(req.getId());
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet()));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req.getId(), event, true, null);

        // Verify Spring event published — @TransactionalEventListener picks it up after commit
        // and calls redemptionEventProducer.publishRedemptionCompleted()
        verify(eventPublisher).publishEvent(any(RedemptionCompletedEvent.class));
    }
}
