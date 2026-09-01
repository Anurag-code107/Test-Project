package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IncentiveExpiringNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncentiveExpiringNotificationScheduler.class);

    private final ClientRepository clientRepository;
    private final IncentiveRepository incentiveRepository;
    private final NotificationEventProducer notificationEventProducer;

    public IncentiveExpiringNotificationScheduler(ClientRepository clientRepository,
                                                    IncentiveRepository incentiveRepository,
                                                    NotificationEventProducer notificationEventProducer) {
        this.clientRepository = clientRepository;
        this.incentiveRepository = incentiveRepository;
        this.notificationEventProducer = notificationEventProducer;
    }

    @Scheduled(cron = "0 30 8 * * *")
    public void checkExpiringIncentives() {
        Instant now = Instant.now();
        Instant sevenDaysFromNow = now.plus(7, ChronoUnit.DAYS);
        List<Client> clients = clientRepository.findAll();
        int totalNotified = 0;

        for (Client client : clients) {
            try {
                TenantContext.setClientId(client.getId());
                int notified = notifyForClient(client.getId(), now, sevenDaysFromNow);
                totalNotified += notified;
            } catch (Exception e) {
                log.error("Failed to check expiring incentives for client {}: {}",
                    client.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (totalNotified > 0) {
            log.info("Published expiring-soon notifications for {} incentive(s)", totalNotified);
        }
    }

    @Transactional(readOnly = true)
    protected int notifyForClient(UUID clientId, Instant now, Instant sevenDaysFromNow) {
        List<Incentive> expiring = incentiveRepository.findActiveWithEndDateBetweenByClientId(
            clientId, now, sevenDaysFromNow);

        for (Incentive incentive : expiring) {
            long daysUntilExpiry = ChronoUnit.DAYS.between(now, incentive.getEndDate());

            notificationEventProducer.publish(new NotificationEvent(
                "INCENTIVE_EXPIRING_SOON", incentive.getClientId(),
                "Incentive Expiring Soon: " + incentive.getName(),
                "Incentive '" + incentive.getName() + "' will expire in " + daysUntilExpiry + " day(s).",
                "INCENTIVE", incentive.getId(), null, null,
                Map.of("incentiveId", incentive.getId().toString())));
        }

        return expiring.size();
    }
}
