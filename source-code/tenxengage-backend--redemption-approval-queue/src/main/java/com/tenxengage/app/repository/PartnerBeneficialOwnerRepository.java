package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerBeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerBeneficialOwnerRepository extends JpaRepository<PartnerBeneficialOwner, UUID> {

    List<PartnerBeneficialOwner> findByKycRecordId(UUID kycRecordId);
}
