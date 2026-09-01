package com.tenxengage.app.dto.response;

import java.util.List;

public record DealQualifierResponse(
        List<QualifiedIncentiveResult> results,
        String partnerRegion,
        String partnerType
) {}
