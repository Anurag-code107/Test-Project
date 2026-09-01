package com.tenxengage.app.entity.enums;

import java.util.List;

public enum HomeIncentiveTypeFilter {
    ALL,
    SALES,
    ENABLEMENT,
    JOURNEYS;

    public List<IncentiveType> toIncentiveTypes() {
        return switch (this) {
            case ALL -> List.of(IncentiveType.values());
            case SALES -> List.of(IncentiveType.SALES);
            case ENABLEMENT -> List.of(IncentiveType.TRAINING, IncentiveType.ACTIVITY);
            case JOURNEYS -> List.of(IncentiveType.JOURNEY);
        };
    }
}
