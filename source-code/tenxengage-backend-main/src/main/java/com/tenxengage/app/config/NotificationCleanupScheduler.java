package com.tenxengage.app.config;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);
    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final NotificationRepository notificationRepository;
    private final ClientRepository clientRepository;

    public NotificationCleanupScheduler(NotificationRepository notificationRepository,
                                         ClientRepository clientRepository) {
        this.notificationRepository = notificationRepository;
        this.clientRepository = clientRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredNotifications() {
        List<UUID> clientIds = notificationRepository.findDistinctClientIds();
        if (clientIds.isEmpty()) {
            return;
        }

        int totalDeleted = 0;
        for (UUID clientId : clientIds) {
            int retentionDays = clientRepository.findById(clientId)
                .map(Client::getNotificationRetentionDays)
                .orElse(DEFAULT_RETENTION_DAYS);

            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = notificationRepository.deleteByClientIdAndCreatedAtBefore(clientId, cutoff);
            totalDeleted += deleted;
        }

        if (totalDeleted > 0) {
            log.info("Notification cleanup: deleted {} expired notifications across {} clients",
                totalDeleted, clientIds.size());
        }
    }
}
