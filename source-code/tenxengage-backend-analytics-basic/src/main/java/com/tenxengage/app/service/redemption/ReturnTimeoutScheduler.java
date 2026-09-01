package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.ReturnEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ReturnTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReturnTimeoutScheduler.class);
    private static final int TIMEOUT_DAYS = 7;
    private static final int PAGE_SIZE = 50;

    private final ClientRepository clientRepository;
    private final RedemptionReturnRepository redemptionReturnRepository;
    private final ReturnEventProducer returnEventProducer;
    private final NotificationEventProducer notificationEventProducer;

    public ReturnTimeoutScheduler(ClientRepository clientRepository,
                                  RedemptionReturnRepository redemptionReturnRepository,
                                  ReturnEventProducer returnEventProducer,
                                  NotificationEventProducer notificationEventProducer) {
        this.clientRepository = clientRepository;
        this.redemptionReturnRepository = redemptionReturnRepository;
        this.returnEventProducer = returnEventProducer;
        this.notificationEventProducer = notificationEventProducer;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void timeoutApprovedReturns() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(TIMEOUT_DAYS, ChronoUnit.DAYS);
        List<Client> clients = clientRepository.findAll();
        int totalTimedOut = 0;

        for (Client client : clients) {
            try {
                TenantContext.setClientId(client.getId());
                totalTimedOut += processForClient(client.getId(), cutoff, now);
            } catch (Exception e) {
                log.error("Failed to process return timeouts for client {}: {}", client.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (totalTimedOut > 0) {
            log.warn("Timed out {} APPROVED return(s) past {}-day vendor response window", totalTimedOut, TIMEOUT_DAYS);
        }
    }

    private int processForClient(UUID clientId, Instant cutoff, Instant now) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        int count = 0;
        Page<RedemptionReturn> page;
        do {
            page = redemptionReturnRepository.findApprovedTimedOut(clientId, cutoff, pageable);
            for (RedemptionReturn ret : page.getContent()) {
                ret.setStatus(ReturnStatus.RETURN_TIMED_OUT);
                ret.setTimedOutAt(now);
                redemptionReturnRepository.save(ret);
                returnEventProducer.publishReturnTimedOut(ret);
                notificationEventProducer.publish(buildTimeoutNotification(ret));
                log.warn("step=return_timed_out returnId={} approvedAt={} timedOutAt={}", ret.getId(), ret.getApprovedAt(), now);
                count++;
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

    private NotificationEvent buildTimeoutNotification(RedemptionReturn ret) {
        return new NotificationEvent(
                "RETURN_TIMED_OUT",
                ret.getClientId(),
                "Return Request Timed Out",
                "Your return request has been pending vendor response for " + TIMEOUT_DAYS + " days.",
                "REDEMPTION_RETURN",
                ret.getId(),
                null,
                List.of(ret.getPartnerUserId()),
                Map.of("returnId", ret.getId().toString(), "redemptionId", ret.getRedemptionId().toString())
        );
    }
}
