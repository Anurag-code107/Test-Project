package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.SyncSchedule;
import com.tenxengage.app.entity.enums.SyncCadence;

import java.time.Instant;
import java.util.UUID;

public record SyncScheduleResponse(
        UUID id,
        UUID dataObjectId,
        boolean enabled,
        SyncCadence cadence,
        Instant lastRunAt,
        Instant nextRunAt
) {
    public static SyncScheduleResponse from(SyncSchedule schedule) {
        return new SyncScheduleResponse(
                schedule.getId(),
                schedule.getDataObject().getId(),
                schedule.isEnabled(),
                schedule.getCadence(),
                schedule.getLastRunAt(),
                schedule.getNextRunAt()
        );
    }
}
