package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncentiveExpirationSchedulerTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private IncentiveRepository incentiveRepository;
    @Mock
    private NotificationEventProducer notificationEventProducer;

    @InjectMocks
    private IncentiveExpirationScheduler scheduler;

    @Test
    void expireActiveIncentivesPastEndDate_setsStatusToInactive() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder().name("Test").subdomain("test").build();
        client.setId(clientId);

        Incentive expired = Incentive.builder()
                .name("Expired Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(clientId)
                .createdBy(UUID.randomUUID())
                .endDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        expired.setId(UUID.randomUUID());

        when(clientRepository.findAll()).thenReturn(List.of(client));
        when(incentiveRepository.findActiveWithEndDateBeforeByClientId(eq(clientId), any(Instant.class)))
                .thenReturn(List.of(expired));

        scheduler.expireActiveIncentivesPastEndDate();

        assertThat(expired.getStatus()).isEqualTo(IncentiveStatus.INACTIVE);
        assertThat(expired.getStatusChangedAt()).isNotNull();
        verify(incentiveRepository).saveAll(List.of(expired));
        verify(notificationEventProducer).publish(any());
    }

    @Test
    void expireActiveIncentivesPastEndDate_doesNothingWhenNoExpired() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder().name("Test").subdomain("test").build();
        client.setId(clientId);

        when(clientRepository.findAll()).thenReturn(List.of(client));
        when(incentiveRepository.findActiveWithEndDateBeforeByClientId(eq(clientId), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.expireActiveIncentivesPastEndDate();

        verify(incentiveRepository, never()).saveAll(any());
        verify(notificationEventProducer, never()).publish(any());
    }

    @Test
    void expireActiveIncentivesPastEndDate_doesNothingWhenNoClients() {
        when(clientRepository.findAll()).thenReturn(List.of());

        scheduler.expireActiveIncentivesPastEndDate();

        verify(incentiveRepository, never()).findActiveWithEndDateBeforeByClientId(any(), any());
    }
}
