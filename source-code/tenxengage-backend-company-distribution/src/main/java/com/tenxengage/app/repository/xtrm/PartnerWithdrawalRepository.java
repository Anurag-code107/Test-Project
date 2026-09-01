package com.tenxengage.app.repository.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerWithdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link PartnerWithdrawal}. Tenant-scoped by {@code clientId}; used for the user's
 * withdrawal-history list (newest first).
 */
@Repository
public interface PartnerWithdrawalRepository extends JpaRepository<PartnerWithdrawal, UUID> {

    Page<PartnerWithdrawal> findByClientIdAndUserIdOrderByCreatedAtDesc(UUID clientId, UUID userId, Pageable pageable);
}
