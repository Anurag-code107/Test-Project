package com.tenxengage.admin.dto.request;

import com.tenxengage.admin.entity.enums.ClientStatus;
import com.tenxengage.admin.entity.enums.SubscriptionTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @NotBlank(message = "Subdomain is required")
    @Size(max = 63)
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$", message = "Subdomain must be lowercase alphanumeric with optional hyphens")
    String subdomain,

    @Size(max = 500)
    String logoUrl,

    ClientStatus status,

    @NotNull(message = "Subscription tier is required")
    SubscriptionTier subscriptionTier
) {}
