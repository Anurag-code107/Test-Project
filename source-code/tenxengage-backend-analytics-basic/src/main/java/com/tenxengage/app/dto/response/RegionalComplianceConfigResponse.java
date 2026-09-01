package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RegionalComplianceConfig;

public record RegionalComplianceConfigResponse(
    String regionCode,
    String regionName,
    boolean privacyNoticeRequired,
    boolean termsOfServiceRequired,
    boolean antiBriberyRequired,
    boolean consentAiVisible,
    boolean consentMarketingVisible,
    boolean consentAnalyticsVisible,
    boolean cookieNoticeVisible
) {

    public static RegionalComplianceConfigResponse from(RegionalComplianceConfig config) {
        return new RegionalComplianceConfigResponse(
            config.getRegionCode(),
            config.getRegionName(),
            config.isPrivacyNoticeRequired(),
            config.isTermsOfServiceRequired(),
            config.isAntiBriberyRequired(),
            config.isConsentAiVisible(),
            config.isConsentMarketingVisible(),
            config.isConsentAnalyticsVisible(),
            config.isCookieNoticeVisible()
        );
    }
}
