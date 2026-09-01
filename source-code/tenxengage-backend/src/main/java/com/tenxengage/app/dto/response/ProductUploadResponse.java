package com.tenxengage.app.dto.response;

import java.util.List;

public record ProductUploadResponse(
    int added,
    int skipped,
    List<ProductResponse> products
) {}
