package com.tenxengage.app.dto.response;

import java.util.List;

public record LocationHierarchyResponse(
    List<LocationLevelResponse> levels,
    List<LocationValueResponse> tree
) {}
