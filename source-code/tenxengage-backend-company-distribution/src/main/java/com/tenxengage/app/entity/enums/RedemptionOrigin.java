package com.tenxengage.app.entity.enums;

/**
 * Which store created a {@link com.tenxengage.app.entity.RedemptionRequest} payout leg.
 *
 * <p>Named {@code RedemptionOrigin} rather than {@code RedemptionRequestType} because that name is
 * already taken by the approval queue's {@code {REDEMPTION, RETURN}} enum.</p>
 *
 * <p><b>{@link #COMPANY_DISTRIBUTION} inverts how the row reads.</b> {@code user_id} is the
 * <em>recipient</em> being paid, not the person who acted, and {@code wallet_id} is the COMPANY wallet
 * the money came from. That inversion is what lets the existing payout pipeline (dispatch, settle,
 * webhook, reconciliation, crash recovery) resolve the payee unchanged. The initiating partner admin is
 * on {@code company_distributions.initiated_by_user_id}, not on the redemption row.</p>
 */
public enum RedemptionOrigin {

    /** Redemption store: a user redeeming their own INDIVIDUAL wallet. The default for every legacy row. */
    SELF,

    /** Distribution store: a partner admin distributing the COMPANY wallet to a partner seller. */
    COMPANY_DISTRIBUTION
}
