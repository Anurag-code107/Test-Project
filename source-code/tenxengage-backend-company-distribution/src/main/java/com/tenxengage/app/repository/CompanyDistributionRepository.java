package com.tenxengage.app.repository;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.enums.DistributionRail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Distribution headers. Every query is scoped by {@code clientId} (defence in depth alongside the Hibernate
 * tenant filter) and, for reads, by {@code partnerCompanyId} — a partner admin must never see another
 * company's distributions.
 */
@Repository
public interface CompanyDistributionRepository extends JpaRepository<CompanyDistribution, UUID> {

    /** Idempotency: a re-POST with the same key returns the original distribution instead of sending twice. */
    Optional<CompanyDistribution> findByClientIdAndClientIdempotencyKey(UUID clientId, String clientIdempotencyKey);

    /** Resolve one header, tenant + company scoped. Never trusts a raw id from the client. */
    Optional<CompanyDistribution> findByIdAndClientIdAndPartnerCompanyId(
            UUID id, UUID clientId, UUID partnerCompanyId);

    /**
     * Distribution History for one company — <b>all</b> its admins' distributions, not just the caller's
     * (design §6.2 A). Several admins share one wallet, so hiding a peer's distribution would leave the
     * balance falling with no explanation; the "Initiated by" column attributes each one instead.
     */
    @Query("""
            SELECT d FROM CompanyDistribution d
            WHERE d.clientId = :clientId
              AND d.partnerCompanyId = :partnerCompanyId
              AND (:rail IS NULL OR d.rail = :rail)
              AND d.createdAt >= COALESCE(:dateFrom, d.createdAt)
              AND d.createdAt <= COALESCE(:dateTo, d.createdAt)
            """)
    Page<CompanyDistribution> findCompanyHistory(
            @Param("clientId") UUID clientId,
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("rail") DistributionRail rail,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
