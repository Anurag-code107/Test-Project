package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.ConnectorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateConnectorRequest(
    @NotNull ConnectorType connectorType,
    @NotBlank @Size(max = 255) String name,
    @NotNull Map<String, String> config,
    String authType
) {}
