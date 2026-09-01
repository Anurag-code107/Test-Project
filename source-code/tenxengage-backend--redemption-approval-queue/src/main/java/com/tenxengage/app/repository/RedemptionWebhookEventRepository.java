package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionWebhookEventRepository extends JpaRepository<RedemptionWebhookEvent, UUID> {

    Optional<RedemptionWebhookEvent> findByIdempotencyKey(String idempotencyKey);

    List<RedemptionWebhookEvent> findByRedemptionRequestIdAndClientId(UUID redemptionRequestId, UUID clientId);

    Page<RedemptionWebhookEvent> findByClientIdAndStatusAndDeletedFalse(UUID clientId, WebhookStatus status, Pageable pageable);
}
