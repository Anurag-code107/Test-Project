package com.tenxengage.app.dto.response.redemption;

import java.util.Map;

public record RedemptionCountDto(
        Long total,
        Map<String, Long> byStatus,
        boolean hasActivity
) {

    /**
     * Builds a count DTO from a status-breakdown map.
     * {@code hasActivity} is {@code false} when the total across all statuses is zero (FR-07.8).
     *
     * @param byStatus count per aggregated status key (PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED)
     */
    public static RedemptionCountDto of(Map<String, Long> byStatus) {
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new RedemptionCountDto(total, Map.copyOf(byStatus), total > 0);
    }
}
