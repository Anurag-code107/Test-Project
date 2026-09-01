package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateConnectorRequest(
    @Size(max = 255) String name,
    Map<String, String> config,
    String authType
) {}
