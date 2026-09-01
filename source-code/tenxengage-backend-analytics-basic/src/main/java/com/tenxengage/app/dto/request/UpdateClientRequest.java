package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
    @Size(max = 255)
    String name,

    @Size(max = 500)
    String logoUrl,

    ClientStatus status,

    SubscriptionTier subscriptionTier
) {}
