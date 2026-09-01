package com.tenxengage.admin.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
    T data,
    String message,
    boolean success,
    String timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, "Success", true, Instant.now().toString());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, message, true, Instant.now().toString());
    }
}
