package com.tenxengage.app.repository.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PartnerLinkedBank}. Every query is tenant-scoped by {@code clientId}
 * (defense-in-depth alongside the Hibernate tenant filter). Soft-deleted rows are excluded automatically
 * by the entity's {@code @SQLRestriction("deleted = false")}.
 */
@Repository
public interface PartnerLinkedBankRepository extends JpaRepository<PartnerLinkedBank, UUID> {

    /** All active linked banks for a user, oldest first — the deterministic auto-promote order. */
    List<PartnerLinkedBank> findByUserIdAndClientIdOrderByCreatedAtAsc(UUID userId, UUID clientId);

    /** Resolve a single bank by our PK, tenant + self scoped (never trusts a raw id from the client). */
    Optional<PartnerLinkedBank> findByIdAndUserIdAndClientId(UUID id, UUID userId, UUID clientId);

    /** Belt-and-suspenders duplicate pre-check (XTRM also rejects a duplicate beneficiary). */
    boolean existsByUserIdAndClientIdAndXtrmBeneficiaryId(UUID userId, UUID clientId, String xtrmBeneficiaryId);
}
