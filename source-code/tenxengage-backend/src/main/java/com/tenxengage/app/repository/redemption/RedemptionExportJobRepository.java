package com.tenxengage.app.repository.redemption;

import com.tenxengage.app.entity.redemption.RedemptionExportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionExportJobRepository extends JpaRepository<RedemptionExportJob, UUID> {

    Optional<RedemptionExportJob> findByIdAndClientId(UUID id, UUID clientId);

    Page<RedemptionExportJob> findByRequestedByIdAndClientId(UUID requestedById, UUID clientId, Pageable pageable);
}
