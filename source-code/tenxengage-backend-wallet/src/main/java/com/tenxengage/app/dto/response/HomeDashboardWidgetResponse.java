package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.HomeDashboardWidget;

import java.util.Set;

public record HomeDashboardWidgetResponse(String key, Set<String> supportedRoleTypes) {

    public static HomeDashboardWidgetResponse from(HomeDashboardWidget widget) {
        return new HomeDashboardWidgetResponse(widget.getKey(), widget.getSupportedRoleTypes());
    }
}
