package com.tenxengage.app.repository;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditActorType;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DB-backed regression test (local Postgres via the {@code localtest} profile) for the
 * nullable-{@code userType} clause of {@link AuditLogRepository#findFiltered}.
 *
 * <p>The clause originally read {@code (:userType IS NULL OR a.userType = :userType)} with no
 * {@code CAST}. PostgreSQL cannot infer the bind type when {@code userType} is {@code null},
 * so the call failed at execution with {@code ERROR: could not determine data type of parameter}
 * (SQLState 42P18) — a 500 on the audit-log listing endpoint. The fix mirrors the sibling
 * clauses: {@code (CAST(:userType AS STRING) IS NULL OR a.userType = :userType)}.
 *
 * <p>This must run against a real database — the bug is in how Postgres infers the parameter
 * type, which an in-memory/mocked layer would not reproduce. It seeds its own {@link Client} +
 * {@link AuditLog} (both rolled back by {@code @Transactional}) rather than relying on
 * pre-seeded data; {@code audit_logs.client_id} FKs {@code clients(id)}, so the client must
 * exist first.
 */
@Tag("integration")
@Transactional
class AuditLogRepositoryFindFilteredIT extends AbstractLocalIntegrationTest {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ClientRepository clientRepository;

    @Test
    void findFiltered_withNullUserType_executesAndReturnsRow() {
        Client client = seedClient();
        AuditLog seeded = seedAuditLog(client.getId());

        // The null-userType path must not throw (42P18 before the CAST fix).
        Page<AuditLog> page = auditLogRepository.findFiltered(
                client.getId(), null, null, null, null, null, PageRequest.of(0, 10));
        assertThatCode(() -> auditLogRepository.findFiltered(
                client.getId(), null, null, null, null, null, PageRequest.of(0, 10)))
                .doesNotThrowAnyException();

        // And it must still return the seeded row for the client (no filter applied).
        assertThat(page.getContent())
                .extracting(AuditLog::getId)
                .contains(seeded.getId());
    }

    // ── fixtures ────────────────────────────────────────────────────────────────

    private Client seedClient() {
        return clientRepository.save(Client.builder()
                .name("Audit FindFiltered IT Client")
                .subdomain("audit-findfiltered-it-" + UUID.randomUUID())
                .subscriptionTier(SubscriptionTier.STARTER)
                .build());
    }

    private AuditLog seedAuditLog(UUID clientId) {
        return auditLogRepository.save(AuditLog.builder()
                .clientId(clientId)
                .actorType(AuditActorType.SYSTEM)
                .userType(null)               // exercises the previously-broken null path
                .action(AuditAction.CREATED)
                .resourceType(AuditResourceType.CLIENT)
                .build());
    }
}
