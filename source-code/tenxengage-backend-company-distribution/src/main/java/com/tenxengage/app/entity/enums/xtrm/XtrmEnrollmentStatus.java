package com.tenxengage.app.entity.enums.xtrm;

/**
 * Lifecycle of a user's XTRM enrollment (stored on {@code partner_redemption.enrollment_status}).
 *
 * <ul>
 *   <li>{@code NOT_ENROLLED} — no XTRM {@code CreateUser} attempt yet.</li>
 *   <li>{@code ENROLLED} — XTRM {@code CreateUser} succeeded; {@code recipient_user_id} (PAT) is stored.</li>
 *   <li>{@code FAILED} — a {@code CreateUser} attempt errored; retryable (lazily before payout).</li>
 * </ul>
 *
 * Enrollment is idempotent: {@code ENROLLED} is terminal (no re-call).
 */
public enum XtrmEnrollmentStatus {
    NOT_ENROLLED,
    ENROLLED,
    FAILED
}
