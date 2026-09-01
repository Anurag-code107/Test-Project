package com.tenxengage.app.dto.response;

import java.util.List;

public record PartnerContextResponse(
        String region,
        String partnerType,
        List<String> customerSegmentOptions
) {}
