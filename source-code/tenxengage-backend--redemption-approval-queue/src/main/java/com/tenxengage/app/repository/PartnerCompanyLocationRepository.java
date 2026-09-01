package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerCompanyLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerCompanyLocationRepository extends JpaRepository<PartnerCompanyLocation, UUID> {

    List<PartnerCompanyLocation> findByPartnerCompanyId(UUID partnerCompanyId);

    List<PartnerCompanyLocation> findByPartnerCompanyIdAndLocationValue_Level_Id(UUID partnerCompanyId, UUID levelId);

    void deleteByPartnerCompanyId(UUID partnerCompanyId);

    List<PartnerCompanyLocation> findByClientIdAndLocationValueId(UUID clientId, UUID locationValueId);
}
