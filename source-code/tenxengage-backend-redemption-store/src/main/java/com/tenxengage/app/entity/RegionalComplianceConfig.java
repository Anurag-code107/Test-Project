package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "regional_compliance_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RegionalComplianceConfig extends BaseEntity {

    @Column(name = "region_code", nullable = false, unique = true, length = 20)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    @Column(name = "privacy_notice_required", nullable = false)
    @Builder.Default
    private boolean privacyNoticeRequired = true;

    @Column(name = "terms_of_service_required", nullable = false)
    @Builder.Default
    private boolean termsOfServiceRequired = true;

    @Column(name = "anti_bribery_required", nullable = false)
    @Builder.Default
    private boolean antiBriberyRequired = true;

    @Column(name = "consent_ai_visible", nullable = false)
    @Builder.Default
    private boolean consentAiVisible = false;

    @Column(name = "consent_marketing_visible", nullable = false)
    @Builder.Default
    private boolean consentMarketingVisible = false;

    @Column(name = "consent_analytics_visible", nullable = false)
    @Builder.Default
    private boolean consentAnalyticsVisible = false;

    @Column(name = "cookie_notice_visible", nullable = false)
    @Builder.Default
    private boolean cookieNoticeVisible = false;
}
