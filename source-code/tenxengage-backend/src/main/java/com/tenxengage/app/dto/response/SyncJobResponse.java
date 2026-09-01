package com.tenxengage.app.dto.response;

import java.util.UUID;

public record SyncJobResponse(UUID jobId, String status) {
}
