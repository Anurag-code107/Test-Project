package com.tenxengage.app.repository;

import com.tenxengage.app.entity.CompanyPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyPermissionOverrideRepository extends JpaRepository<CompanyPermissionOverride, UUID> {

    List<CompanyPermissionOverride> findByClientIdAndPartnerCompanyId(UUID clientId, UUID partnerCompanyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CompanyPermissionOverride o WHERE o.clientId = :clientId AND o.partnerCompanyId = :partnerCompanyId")
    void deleteByClientIdAndPartnerCompanyId(UUID clientId, UUID partnerCompanyId);
}
