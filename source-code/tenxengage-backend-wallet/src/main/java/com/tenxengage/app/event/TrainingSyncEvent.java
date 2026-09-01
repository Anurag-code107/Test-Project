package com.tenxengage.app.event;

import java.util.List;
import java.util.UUID;

public record TrainingSyncEvent(
    UUID clientId,
    UUID dataUploadId,
    List<TrainingCompletionRecord> records
) {
    public record TrainingCompletionRecord(
        String externalUserId
    ) {}
}
