package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.service.ReturnEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnTimeoutSchedulerTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private RedemptionReturnRepository redemptionReturnRepository;
    @Mock
    private ReturnEventProducer returnEventProducer;
    @Mock
    private NotificationEventProducer notificationEventProducer;

    @InjectMocks
    private ReturnTimeoutScheduler scheduler;

    @Test
    void timeoutApprovedReturns_transitionsReturnsBeyond7Days() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder().name("Acme").subdomain("acme").build();
        client.setId(clientId);

        RedemptionReturn timedOut = RedemptionReturn.builder()
                .clientId(clientId)
                .redemptionId(UUID.randomUUID())
                .partnerUserId(UUID.randomUUID())
                .status(ReturnStatus.APPROVED)
                .approvedAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .amount(new BigDecimal("100.0000"))
                .currencyId("points")
                .build();
        timedOut.setId(UUID.randomUUID());

        when(clientRepository.findAll()).thenReturn(List.of(client));
        when(redemptionReturnRepository.findApprovedTimedOut(eq(clientId), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(timedOut)));

        scheduler.timeoutApprovedReturns();

        assertThat(timedOut.getStatus()).isEqualTo(ReturnStatus.RETURN_TIMED_OUT);
        assertThat(timedOut.getTimedOutAt()).isNotNull();
        verify(redemptionReturnRepository).save(timedOut);
        verify(returnEventProducer).publishReturnTimedOut(timedOut);
    }

    @Test
    void timeoutApprovedReturns_doesNotTransitionReturnsWithin7Days() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder().name("Acme").subdomain("acme").build();
        client.setId(clientId);

        when(clientRepository.findAll()).thenReturn(List.of(client));
        when(redemptionReturnRepository.findApprovedTimedOut(eq(clientId), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        scheduler.timeoutApprovedReturns();

        verify(redemptionReturnRepository, never()).save(any());
        verify(returnEventProducer, never()).publishReturnTimedOut(any());
    }

    @Test
    void timeoutApprovedReturns_publishesOneEventPerTimedOutReturn() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder().name("Acme").subdomain("acme").build();
        client.setId(clientId);

        RedemptionReturn ret1 = RedemptionReturn.builder()
                .clientId(clientId).redemptionId(UUID.randomUUID()).partnerUserId(UUID.randomUUID())
                .status(ReturnStatus.APPROVED).approvedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .amount(new BigDecimal("50.0000")).currencyId("points").build();
        ret1.setId(UUID.randomUUID());

        RedemptionReturn ret2 = RedemptionReturn.builder()
                .clientId(clientId).redemptionId(UUID.randomUUID()).partnerUserId(UUID.randomUUID())
                .status(ReturnStatus.APPROVED).approvedAt(Instant.now().minus(9, ChronoUnit.DAYS))
                .amount(new BigDecimal("75.0000")).currencyId("points").build();
        ret2.setId(UUID.randomUUID());

        when(clientRepository.findAll()).thenReturn(List.of(client));
        when(redemptionReturnRepository.findApprovedTimedOut(eq(clientId), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ret1, ret2)));

        scheduler.timeoutApprovedReturns();

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnEventProducer, times(2)).publishReturnTimedOut(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(ret1, ret2);
    }

    @Test
    void timeoutApprovedReturns_doesNothingWhenNoClients() {
        when(clientRepository.findAll()).thenReturn(List.of());

        scheduler.timeoutApprovedReturns();

        verify(redemptionReturnRepository, never()).findApprovedTimedOut(any(), any(), any());
        verify(returnEventProducer, never()).publishReturnTimedOut(any());
    }
}
