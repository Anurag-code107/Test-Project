package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;

/**
 * Safe view of a user's payout profile. Exposes only what the payout UI needs; deliberately
 * <b>never</b> includes {@code recipientUserId} (PAT), {@code partnerLinkedBankId}, {@code clientId},
 * {@code version}, {@code enrollmentError}, or any bank/card number. Includes the payout <b>address</b>
 * (the user's own PII, on a self-only endpoint) so the profile screen can pre-fill it.
 *
 * <p>{@code enrollmentStatus} is the raw {@link XtrmEnrollmentStatus} enum; the FE maps it to the
 * display labels (ENROLLED → "Ready", NOT_ENROLLED → "Pending", FAILED → "Action needed").</p>
 */
public record RedemptionProfileResponse(
        XtrmEnrollmentStatus enrollmentStatus,
        RedemptionPayoutMethod payoutMethod,
        boolean bankLinked,
        String linkedBankLabel,
        boolean cardLinked,
        String linkedCardLabel,
        String identityLevel,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        String countryIso2) {

    public static RedemptionProfileResponse from(PartnerRedemption profile) {
        String linkedBankId = profile.getPartnerLinkedBankId();
        boolean bankLinked = linkedBankId != null && !linkedBankId.isBlank();
        String linkedCardId = profile.getPartnerLinkedCardId();
        boolean cardLinked = linkedCardId != null && !linkedCardId.isBlank();
        PartnerAddress address = profile.getAddress();
        return new RedemptionProfileResponse(
                profile.getEnrollmentStatus(),
                profile.getPayoutMethod(),
                bankLinked,
                profile.getLinkedBankLabel(),
                cardLinked,
                profile.getLinkedCardLabel(),
                profile.getIdentityLevel(),
                address == null ? null : address.getLine1(),
                address == null ? null : address.getLine2(),
                address == null ? null : address.getCity(),
                address == null ? null : address.getRegion(),
                address == null ? null : address.getPostalCode(),
                address == null ? null : address.getCountryIso2());
    }
}
