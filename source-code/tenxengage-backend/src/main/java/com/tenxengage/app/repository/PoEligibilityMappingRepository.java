package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PoEligibilityMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoEligibilityMappingRepository extends JpaRepository<PoEligibilityMapping, UUID> {

    List<PoEligibilityMapping> findByPurchaseOrderId(UUID purchaseOrderId);

    List<PoEligibilityMapping> findByPurchaseOrderIdAndEligible(UUID purchaseOrderId, Boolean eligible);

    Optional<PoEligibilityMapping> findByPurchaseOrderIdAndIncentiveId(UUID purchaseOrderId, UUID incentiveId);

    List<PoEligibilityMapping> findByClientIdAndIncentiveId(UUID clientId, UUID incentiveId);

    @Query("SELECT pem FROM PoEligibilityMapping pem LEFT JOIN FETCH pem.payouts " +
           "WHERE pem.purchaseOrderId = :poId")
    List<PoEligibilityMapping> findByPurchaseOrderIdWithPayouts(@Param("poId") UUID poId);

    void deleteByPurchaseOrderId(UUID purchaseOrderId);

    long countByClientIdAndEligible(UUID clientId, Boolean eligible);
}
