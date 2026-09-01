package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerCompanyRepository extends JpaRepository<PartnerCompany, UUID> {

    Page<PartnerCompany> findByClientId(UUID clientId, Pageable pageable);

    Optional<PartnerCompany> findByIdAndClientId(UUID id, UUID clientId);

    boolean existsByClientIdAndName(UUID clientId, String name);

    boolean existsByClientIdAndExternalPartnerId(UUID clientId, String externalPartnerId);

    @Query("""
        SELECT pc FROM PartnerCompany pc
        WHERE pc.clientId = :clientId
        AND (:search IS NULL OR :search = ''
            OR LOWER(pc.name) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (CAST(:status AS STRING) IS NULL OR pc.status = :status)
        """)
    Page<PartnerCompany> searchByClientId(@Param("clientId") UUID clientId,
                                           @Param("search") String search,
                                           @Param("status") PartnerCompanyStatus status,
                                           Pageable pageable);
}
