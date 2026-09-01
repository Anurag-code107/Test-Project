package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PartnerProgramAcknowledgment;

import java.time.Instant;
import java.util.UUID;

public record AcknowledgmentResponse(
    UUID id,
    UUID clientId,
    UUID partnerCompanyId,
    UUID incentiveId,
    UUID acknowledgedBy,
    Instant acknowledgedAt,
    String policyVersion
) {

    public static AcknowledgmentResponse from(PartnerProgramAcknowledgment ack) {
        return new AcknowledgmentResponse(
            ack.getId(),
            ack.getClientId(),
            ack.getPartnerCompanyId(),
            ack.getIncentiveId(),
            ack.getAcknowledgedBy(),
            ack.getAcknowledgedAt(),
            ack.getPolicyVersion()
        );
    }
}
