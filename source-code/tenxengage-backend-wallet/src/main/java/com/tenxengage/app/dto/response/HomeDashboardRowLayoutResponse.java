package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.HomeDashboardRowLayout;

public record HomeDashboardRowLayoutResponse(String key, int slotCount) {

    public static HomeDashboardRowLayoutResponse from(HomeDashboardRowLayout layout) {
        return new HomeDashboardRowLayoutResponse(layout.getKey(), layout.getSlotCount());
    }
}
