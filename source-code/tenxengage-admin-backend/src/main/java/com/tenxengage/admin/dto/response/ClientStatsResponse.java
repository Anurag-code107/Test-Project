package com.tenxengage.admin.dto.response;

import java.util.Map;

public record ClientStatsResponse(
    long totalClients,
    Map<String, Long> countByStatus,
    Map<String, Long> countByTier
) {}
