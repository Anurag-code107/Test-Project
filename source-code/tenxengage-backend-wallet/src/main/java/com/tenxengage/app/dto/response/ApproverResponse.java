package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.IncentiveApprover;

import java.util.UUID;

public record ApproverResponse(
    UUID id,
    String email,
    String category,
    Integer sortOrder
) {
    public static ApproverResponse from(IncentiveApprover approver) {
        return new ApproverResponse(
            approver.getId(),
            approver.getEmail(),
            approver.getCategory(),
            approver.getSortOrder()
        );
    }
}
