package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.SyncCadence;
import jakarta.validation.constraints.NotNull;

public record UpdateSyncScheduleRequest(
        @NotNull Boolean enabled,
        @NotNull SyncCadence cadence
) {}
