package com.tenxengage.admin.dto.response;

import com.tenxengage.admin.entity.Client;
import com.tenxengage.admin.entity.enums.ClientStatus;
import com.tenxengage.admin.entity.enums.SubscriptionTier;

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
