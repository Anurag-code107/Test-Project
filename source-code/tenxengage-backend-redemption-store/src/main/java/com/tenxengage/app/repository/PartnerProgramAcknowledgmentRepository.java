package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerProgramAcknowledgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerProgramAcknowledgmentRepository extends JpaRepository<PartnerProgramAcknowledgment, UUID> {

    boolean existsByPartnerCompanyIdAndIncentiveId(UUID partnerCompanyId, UUID incentiveId);

    List<PartnerProgramAcknowledgment> findByIncentiveId(UUID incentiveId);

    List<PartnerProgramAcknowledgment> findByPartnerCompanyId(UUID partnerCompanyId);
}
