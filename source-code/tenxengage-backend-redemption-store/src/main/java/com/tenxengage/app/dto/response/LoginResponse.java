package com.tenxengage.app.dto.response;

import java.util.List;

public record LoginResponse(
    long expiresIn,
    UserResponse user,
    List<String> enabledFeatures
) {}
