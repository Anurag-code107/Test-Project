package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
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
import java.util.List;
import java.util.Map;

@Component
public class IncentiveExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncentiveExpirationScheduler.class);

    private final ClientRepository clientRepository;
    private final IncentiveRepository incentiveRepository;
    private final NotificationEventProducer notificationEventProducer;

    public IncentiveExpirationScheduler(ClientRepository clientRepository,
                                         IncentiveRepository incentiveRepository,
                                         NotificationEventProducer notificationEventProducer) {
        this.clientRepository = clientRepository;
        this.incentiveRepository = incentiveRepository;
        this.notificationEventProducer = notificationEventProducer;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void expireActiveIncentivesPastEndDate() {
        Instant now = Instant.now();
        List<Client> clients = clientRepository.findAll();
        int totalExpired = 0;

        for (Client client : clients) {
            try {
                TenantContext.setClientId(client.getId());
                int expired = expireForClient(client.getId(), now);
                totalExpired += expired;
            } catch (Exception e) {
                log.error("Failed to expire incentives for client {}: {}",
                    client.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (totalExpired > 0) {
            log.info("Auto-expired {} ACTIVE incentive(s) past their end date", totalExpired);
        }
    }

    @Transactional
    protected int expireForClient(java.util.UUID clientId, Instant now) {
        List<Incentive> expired = incentiveRepository.findActiveWithEndDateBeforeByClientId(clientId, now);
        if (expired.isEmpty()) {
            return 0;
        }
        for (Incentive incentive : expired) {
            incentive.setStatus(IncentiveStatus.INACTIVE);
            incentive.setStatusChangedAt(now);

            notificationEventProducer.publish(new NotificationEvent(
                "INCENTIVE_EXPIRED", incentive.getClientId(),
                "Incentive Expired: " + incentive.getName(),
                "Incentive '" + incentive.getName() + "' has expired.",
                "INCENTIVE", incentive.getId(), null, null,
                Map.of("incentiveId", incentive.getId().toString())));
        }
        incentiveRepository.saveAll(expired);
        return expired.size();
    }
}
