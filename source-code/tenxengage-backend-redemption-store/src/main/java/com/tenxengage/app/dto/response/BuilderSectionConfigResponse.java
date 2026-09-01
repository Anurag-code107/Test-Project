package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.BuilderSectionConfig;

import java.util.List;
import java.util.UUID;

public record BuilderSectionConfigResponse(
    UUID id,
    String incentiveType,
    String sectionKey,
    String displayName,
    String subtitle,
    int sortOrder,
    boolean isLocked,
    boolean isVisible,
    List<BuilderFieldConfigResponse> fields
) {
    public static BuilderSectionConfigResponse from(BuilderSectionConfig section) {
        return new BuilderSectionConfigResponse(
            section.getId(),
            section.getIncentiveType(),
            section.getSectionKey(),
            section.getDisplayName(),
            section.getSubtitle(),
            section.getSortOrder(),
            section.isLocked(),
            section.isVisible(),
            section.getFields().stream()
                .map(BuilderFieldConfigResponse::from)
                .toList()
        );
    }
}
