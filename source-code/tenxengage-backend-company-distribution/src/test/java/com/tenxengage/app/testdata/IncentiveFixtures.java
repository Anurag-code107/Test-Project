package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class IncentiveFixtures {

    private IncentiveFixtures() {
    }

    public static Incentive.IncentiveBuilder draft(UUID clientId, UUID createdBy) {
        return Incentive.builder()
                .name("Test Incentive " + UUID.randomUUID().toString().substring(0, 8))
                .description("Test incentive for unit tests")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(clientId)
                .createdBy(createdBy)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(90, ChronoUnit.DAYS));
    }

    public static Incentive.IncentiveBuilder active(UUID clientId, UUID createdBy) {
        return draft(clientId, createdBy)
                .status(IncentiveStatus.ACTIVE)
                .statusChangedAt(Instant.now());
    }

    public static Incentive.IncentiveBuilder pendingApproval(UUID clientId, UUID createdBy) {
        return draft(clientId, createdBy)
                .status(IncentiveStatus.PENDING_APPROVAL)
                .requiresApproval(true)
                .requiredApprovals(1);
    }

    public static Incentive.IncentiveBuilder expired(UUID clientId, UUID createdBy) {
        return Incentive.builder()
                .name("Expired Incentive " + UUID.randomUUID().toString().substring(0, 8))
                .description("An expired incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(clientId)
                .createdBy(createdBy)
                .startDate(Instant.now().minus(180, ChronoUnit.DAYS))
                .endDate(Instant.now().minus(1, ChronoUnit.DAYS));
    }
}
