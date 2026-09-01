package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    String name,
    String subdomain,
    String logoUrl,
    ClientStatus status,
    SubscriptionTier subscriptionTier,
    Instant createdAt,
    Instant updatedAt
) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
            client.getId(),
            client.getName(),
            client.getSubdomain(),
            client.getLogoUrl(),
            client.getStatus(),
            client.getSubscriptionTier(),
            client.getCreatedAt(),
            client.getUpdatedAt()
        );
    }
}
