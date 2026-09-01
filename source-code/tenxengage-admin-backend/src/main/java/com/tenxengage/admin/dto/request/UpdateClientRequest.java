package com.tenxengage.admin.dto.request;

import com.tenxengage.admin.entity.enums.ClientStatus;
import com.tenxengage.admin.entity.enums.SubscriptionTier;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
    @Size(max = 255)
    String name,

    @Size(max = 500)
    String logoUrl,

    ClientStatus status,

    SubscriptionTier subscriptionTier
) {}
