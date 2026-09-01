package com.tenxengage.app.dto.response;

import java.util.List;

public record ApprovalStatusResponse(
    Integer requiredApprovals,
    int approvedCount,
    int rejectedCount,
    int pendingCount,
    List<ApproverStatusResponse> approvers
) {}
