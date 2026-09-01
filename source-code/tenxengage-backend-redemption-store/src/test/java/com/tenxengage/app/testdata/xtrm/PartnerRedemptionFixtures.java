package com.tenxengage.app.testdata.xtrm;

import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;

import java.util.UUID;

/**
 * Test fixtures for {@link PartnerRedemption} (builder-return pattern).
 * Callers may further customise the returned builder before {@code .build()}.
 */
public final class PartnerRedemptionFixtures {

    private PartnerRedemptionFixtures() {
    }

    /** A not-yet-enrolled payout profile shell for the given user/tenant (default AnyPay). */
    public static PartnerRedemption.PartnerRedemptionBuilder notEnrolled(UUID clientId, UUID userId) {
        return PartnerRedemption.builder()
                .clientId(clientId)
                .userId(userId)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .payoutMethod(RedemptionPayoutMethod.ANYPAY)
                .address(PartnerAddress.builder()
                        .line1("123 Main St")
                        .city("Los Angeles")
                        .region("CA")
                        .postalCode("90001")
                        .countryIso2("US")
                        .build());
    }

    /** An enrolled profile with a stored XTRM PAT (AnyPay by default). */
    public static PartnerRedemption.PartnerRedemptionBuilder enrolled(UUID clientId, UUID userId, String recipientUserId) {
        return notEnrolled(clientId, userId)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId(recipientUserId)
                .identityLevel("Standard");
    }

    /** An enrolled profile with a linked bank (BANK rail). */
    public static PartnerRedemption.PartnerRedemptionBuilder enrolledWithBank(UUID clientId, UUID userId,
                                                                        String recipientUserId, String linkedBankId) {
        return enrolled(clientId, userId, recipientUserId)
                .payoutMethod(RedemptionPayoutMethod.BANK)
                .partnerLinkedBankId(linkedBankId)
                .linkedBankLabel("Wells Fargo ••1898");
    }
}
