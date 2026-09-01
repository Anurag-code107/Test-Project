package com.tenxengage.app.entity.enums;

public enum AuditResourceType {
    INCENTIVE,
    USER,
    CLAIM,
    CONNECTOR,
    PARTNER_COMPANY,
    PRODUCT,
    DATA,
    DATA_OBJECT,
    NOTIFICATION_CONFIG,
    CLIENT,
    AUTH,
    REWARD_WALLET,
    REDEMPTION_CATALOG_ITEM,
    TENANT_CATALOG_CONFIG,
    TENANT_REDEMPTION_SETTINGS,
    REDEMPTION_REQUEST,
    REDEMPTION_WEBHOOK_EVENT,
    REDEMPTION_EXPORT_JOB,
    REDEMPTION_RETURN,
    REDEMPTION_ANALYTICS_EXPORT,
    REDEMPTION_ADVANCED_ANALYTICS_EXPORT,
    BALANCE_EXPIRATION_POLICY,
    BALANCE_EXPIRY_BREAKAGE_EXPORT,
    // XTRM payout enhancement (F-03) — per-user XTRM payout profile (partner_redemption)
    PARTNER_REDEMPTION,
    // Subjects that were already named by @Audited but had no constant, so their audit rows were
    // silently dropped by the aspect. Found by AuditedAnnotationsResolveTest.
    BRANDING,
    COMPANY_DISTRIBUTION,
    CURRENCY,
    FISCAL_YEAR_CONFIG,
    LOCATION_LEVEL,
    LOCATION_VALUE,
    PARTNER_WITHDRAWAL
}
