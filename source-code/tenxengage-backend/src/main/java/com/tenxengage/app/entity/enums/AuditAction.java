package com.tenxengage.app.entity.enums;

public enum AuditAction {
    CREATED,
    EDITED,
    DELETED,
    ACTIVATED,
    DEACTIVATED,
    SUBMITTED,
    APPROVED,
    REJECTED,
    EXPIRED,
    CLAIMED,
    UNCLAIMED,
    UPLOADED,
    SYNCED,
    LOGGED_IN,
    LOGGED_OUT,
    ANONYMIZED,
    DATA_EXPORTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    // XTRM payout enhancement (F-03) — see docs/superpowers/plans/2026-07-03-redemption-xtrm-payout-enhancement.md
    ENROLLED,
    BANK_LINKED,
    BANK_UNLINKED,
    // Card instruments + wallet withdrawal — see docs/superpowers/plans/2026-07-15-wallet-withdrawal.md
    CARD_LINKED,
    CARD_UNLINKED,
    WITHDRAWAL
}
