package com.tenxengage.app.dto.response;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    String errorCode,
    String errorMessage,
    int status,
    Instant timestamp,
    String path,
    Map<String, String> details
) {

    public static ErrorResponse of(String errorCode, String errorMessage, int status, String path) {
        return new ErrorResponse(errorCode, errorMessage, status, Instant.now(), path, null);
    }

    public static ErrorResponse of(String errorCode, String errorMessage, int status, String path, Map<String, String> details) {
        return new ErrorResponse(errorCode, errorMessage, status, Instant.now(), path, details);
    }
}
