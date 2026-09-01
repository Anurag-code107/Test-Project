package com.tenxengage.app.entity.enums;

import java.util.Arrays;
import java.util.Optional;

public enum HomeDashboardRowLayout {
    FULL("full", 1),
    HALF_HALF("half-half", 2);

    private final String key;
    private final int slotCount;

    HomeDashboardRowLayout(String key, int slotCount) {
        this.key = key;
        this.slotCount = slotCount;
    }

    public String getKey() {
        return key;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public static Optional<HomeDashboardRowLayout> fromKey(String key) {
        return Arrays.stream(values())
                .filter(l -> l.key.equals(key))
                .findFirst();
    }
}
