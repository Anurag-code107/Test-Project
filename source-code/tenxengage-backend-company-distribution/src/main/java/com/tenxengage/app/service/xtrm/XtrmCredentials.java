package com.tenxengage.app.service.xtrm;

/**
 * Who an XTRM call authenticates as, and which wallet it spends.
 *
 * <p>These five values travel together for a reason: XTRM validates the wallet against the authenticated
 * account, so a client id from one account paired with a wallet id from another is rejected with
 * {@code 400 Invalid wallet id}. Passing them as one object makes that mispairing impossible, which the
 * previous design — five independent {@code @Value} fields — could not guarantee.</p>
 *
 * @param clientId            OAuth client id. Also the cache key for the access token.
 * @param clientSecret        OAuth client secret. <b>Secret.</b>
 * @param issuerAccountNumber the SPN money moves from
 * @param walletId            the wallet money moves from; must belong to {@code issuerAccountNumber}
 * @param programId           the XTRM program the payment is booked under
 */
public record XtrmCredentials(
        String clientId,
        String clientSecret,
        String issuerAccountNumber,
        String walletId,
        String programId
) {

    /**
     * Redacted on purpose.
     *
     * <p>A record's generated {@code toString()} prints every component, so the default would put the client
     * secret into any log line, exception message or debugger view that touched this object — and credentials
     * are handled on the payout path, which is exactly where things get logged when they go wrong.</p>
     */
    @Override
    public String toString() {
        return "XtrmCredentials[clientId=" + clientId
                + ", issuerAccountNumber=" + issuerAccountNumber
                + ", walletId=" + walletId
                + ", programId=" + programId
                + ", clientSecret=***]";
    }
}
