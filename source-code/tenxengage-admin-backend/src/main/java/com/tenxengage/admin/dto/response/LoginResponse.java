package com.tenxengage.admin.dto.response;

import java.util.List;

public record LoginResponse(
    long expiresIn,
    UserResponse user,
    List<String> enabledFeatures
) {}
