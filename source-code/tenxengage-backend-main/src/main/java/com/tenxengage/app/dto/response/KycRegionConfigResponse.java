package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.KycRegionConfig;

import java.util.UUID;

public record KycRegionConfigResponse(
    UUID id,
    String regionCode,
    boolean tier1Required,
    boolean tier2Required
) {

    public static KycRegionConfigResponse from(KycRegionConfig config) {
        return new KycRegionConfigResponse(
            config.getId(),
            config.getRegionCode(),
            config.isTier1Required(),
            config.isTier2Required()
        );
    }
}
