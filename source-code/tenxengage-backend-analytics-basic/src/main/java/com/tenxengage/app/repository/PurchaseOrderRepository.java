package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndClientId(UUID id, UUID clientId);

    long countByClientId(UUID clientId);
}
