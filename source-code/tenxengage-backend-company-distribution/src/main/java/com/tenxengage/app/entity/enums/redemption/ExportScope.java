package com.tenxengage.app.entity.enums.redemption;

/**
 * The scope a redemption-history export runs at. Sent by the client to indicate which tab
 * (Personal / Company / All-tenant) the export was triggered from. The server honors it only
 * up to the caller's permissions — an unauthorized scope is silently narrowed, never widened.
 */
public enum ExportScope {
    PERSONAL,
    /* COMPANY removed (design §12.3) — company-wallet exports no longer exist. The async
       worker still recognises the stored string "COMPANY" so pre-existing jobs drain correctly. */
    ALL_TENANT
}
