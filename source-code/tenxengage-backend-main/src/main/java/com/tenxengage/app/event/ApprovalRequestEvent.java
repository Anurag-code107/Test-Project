package com.tenxengage.app.event;

import java.util.List;
import java.util.UUID;

public record ApprovalRequestEvent(
    UUID incentiveId,
    String incentiveName,
    int approvalRound,
    List<ApproverInfo> approvers
) {
    public record ApproverInfo(String email, String category) {}
}
