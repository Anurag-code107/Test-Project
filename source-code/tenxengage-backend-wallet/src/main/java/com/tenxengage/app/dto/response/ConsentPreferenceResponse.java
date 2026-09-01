package com.tenxengage.app.dto.response;

import java.time.Instant;

public record ConsentPreferenceResponse(
    String consentType,
    boolean granted,
    Instant lastUpdated
) {
}
