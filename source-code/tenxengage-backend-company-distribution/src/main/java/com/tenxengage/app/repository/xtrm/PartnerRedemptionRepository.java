package com.tenxengage.app.repository.xtrm;

import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PartnerRedemption}. Every query is tenant-scoped by {@code clientId}
 * (defense-in-depth alongside the Hibernate tenant filter).
 */
@Repository
public interface PartnerRedemptionRepository extends JpaRepository<PartnerRedemption, UUID> {

    /** Fetch a user's payout profile (self / per-user lookups). */
    Optional<PartnerRedemption> findByUserIdAndClientId(UUID userId, UUID clientId);

    /** Idempotency pre-check; pair with a DataIntegrityViolationException catch on uq_partner_redemption_user_id. */
    boolean existsByUserIdAndClientId(UUID userId, UUID clientId);

    /** Lazy-enrollment backfill sweep (FR-11) — e.g. status = FAILED / NOT_ENROLLED for a tenant. */
    Page<PartnerRedemption> findByClientIdAndEnrollmentStatus(UUID clientId, XtrmEnrollmentStatus enrollmentStatus, Pageable pageable);
}
