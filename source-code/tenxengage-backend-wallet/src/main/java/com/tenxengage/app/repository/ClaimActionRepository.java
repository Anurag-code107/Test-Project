package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClaimAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimActionRepository extends JpaRepository<ClaimAction, UUID> {

    boolean existsByClientIdAndPurchaseOrderIdAndUserId(UUID clientId, UUID purchaseOrderId, UUID userId);

    long countByClientIdAndPurchaseOrderId(UUID clientId, UUID purchaseOrderId);

    List<ClaimAction> findByClientIdAndPurchaseOrderId(UUID clientId, UUID purchaseOrderId);

    List<ClaimAction> findByClientIdAndUserId(UUID clientId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ca FROM ClaimAction ca WHERE ca.clientId = :clientId AND ca.purchaseOrderId = :poId")
    List<ClaimAction> findByClientIdAndPurchaseOrderIdForUpdate(
            @Param("clientId") UUID clientId, @Param("poId") UUID poId);

    /**
     * Check if a user has any claim action for a purchase order that is eligible
     * for a given sales incentive (via po_eligibility_mappings).
     */
    @Query("""
        SELECT CASE WHEN COUNT(ca) > 0 THEN true ELSE false END
        FROM ClaimAction ca
        JOIN PoEligibilityMapping pem ON pem.purchaseOrderId = ca.purchaseOrderId
        WHERE ca.clientId = :clientId
        AND ca.userId = :userId
        AND pem.incentiveId = :incentiveId
        """)
    boolean existsClaimForEligiblePo(@Param("clientId") UUID clientId,
                                      @Param("userId") UUID userId,
                                      @Param("incentiveId") UUID incentiveId);
}
