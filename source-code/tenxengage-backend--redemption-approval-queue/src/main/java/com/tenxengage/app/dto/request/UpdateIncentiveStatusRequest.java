package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.IncentiveStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncentiveStatusRequest(
    @NotNull IncentiveStatus status
) {
}
