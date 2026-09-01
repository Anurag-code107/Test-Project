package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerKycRecord;
import com.tenxengage.app.entity.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerKycRecordRepository extends JpaRepository<PartnerKycRecord, UUID> {

    Optional<PartnerKycRecord> findByPartnerCompanyId(UUID partnerCompanyId);

    List<PartnerKycRecord> findByClientIdAndKycStatus(UUID clientId, KycStatus kycStatus);
}
