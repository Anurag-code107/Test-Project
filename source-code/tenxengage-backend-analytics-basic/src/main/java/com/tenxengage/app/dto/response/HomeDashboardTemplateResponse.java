package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.model.HomeDashboardLayoutPayload;
import com.tenxengage.app.entity.HomeDashboardTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeDashboardTemplateResponse(
        UUID id,
        UUID clientId,
        String name,
        String description,
        String roleType,
        HomeDashboardLayoutPayload layout,
        boolean isSystem,
        Instant createdAt,
        Instant updatedAt) {

    public static HomeDashboardTemplateResponse from(HomeDashboardTemplate template, ObjectMapper objectMapper) {
        HomeDashboardLayoutPayload payload;
        try {
            payload = objectMapper.readValue(template.getLayout(), HomeDashboardLayoutPayload.class);
        } catch (JsonProcessingException e) {
            payload = new HomeDashboardLayoutPayload(List.of());
        }
        return new HomeDashboardTemplateResponse(
                template.getId(),
                template.getClientId(),
                template.getName(),
                template.getDescription(),
                template.getRoleType(),
                payload,
                template.isSystem(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
