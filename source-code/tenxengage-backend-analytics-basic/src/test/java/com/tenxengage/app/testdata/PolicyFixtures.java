package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.LegalPolicy;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.enums.PolicyType;

import java.util.UUID;

public final class PolicyFixtures {

    private PolicyFixtures() {
    }

    public static LegalPolicy.LegalPolicyBuilder privacyNotice(UUID clientId) {
        return LegalPolicy.builder()
                .clientId(clientId)
                .policyType(PolicyType.PRIVACY_NOTICE)
                .version("1.0")
                .title("Privacy Notice")
                .contentUrl("https://example.com/privacy")
                .summary("Privacy notice summary")
                .active(true);
    }

    public static LegalPolicy.LegalPolicyBuilder termsOfService(UUID clientId) {
        return LegalPolicy.builder()
                .clientId(clientId)
                .policyType(PolicyType.TERMS_OF_SERVICE)
                .version("1.0")
                .title("Terms of Service")
                .contentUrl("https://example.com/terms")
                .summary("Terms of service summary")
                .active(true);
    }

    public static LegalPolicy.LegalPolicyBuilder antiBribery(UUID clientId) {
        return LegalPolicy.builder()
                .clientId(clientId)
                .policyType(PolicyType.ANTI_BRIBERY_POLICY)
                .version("1.0")
                .title("Anti-Bribery Policy")
                .contentUrl("https://example.com/anti-bribery")
                .summary("Anti-bribery policy summary")
                .active(true);
    }

    public static RegionalComplianceConfig.RegionalComplianceConfigBuilder usRegion() {
        return RegionalComplianceConfig.builder()
                .regionCode("US")
                .regionName("United States")
                .privacyNoticeRequired(true)
                .termsOfServiceRequired(true)
                .antiBriberyRequired(false)
                .consentAiVisible(true)
                .consentMarketingVisible(true)
                .consentAnalyticsVisible(true)
                .cookieNoticeVisible(false);
    }

    public static RegionalComplianceConfig.RegionalComplianceConfigBuilder euRegion() {
        return RegionalComplianceConfig.builder()
                .regionCode("EU")
                .regionName("European Union")
                .privacyNoticeRequired(true)
                .termsOfServiceRequired(true)
                .antiBriberyRequired(true)
                .consentAiVisible(true)
                .consentMarketingVisible(true)
                .consentAnalyticsVisible(true)
                .cookieNoticeVisible(true);
    }
}
