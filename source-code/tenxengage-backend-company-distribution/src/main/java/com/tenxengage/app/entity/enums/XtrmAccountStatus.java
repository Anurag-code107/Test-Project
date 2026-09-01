package com.tenxengage.app.entity.enums;

/**
 * Whether a partner company can pay its sellers from its own XTRM wallet.
 *
 * <p>XTRM issues a company's pseudo credentials only after that company is separately onboarded,
 * KYC-verified and linked to the managing account. This status tracks where a company sits in that
 * process, and is therefore also the per-company switch for the XTRM-backed distribution rails.</p>
 */
public enum XtrmAccountStatus {

    /** Identity known, credentials not yet issued by XTRM. Cannot pay. */
    PENDING,

    /** Credentials present and usable. May pay from its own wallet. */
    CONNECTED,

    /** Deliberately switched off — revoked, suspended, or offboarded. Cannot pay. */
    DISABLED
}
