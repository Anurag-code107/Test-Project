package com.tenxengage.app.repository;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerCompanyXtrmAccountRepository extends JpaRepository<PartnerCompanyXtrmAccount, UUID> {

    /** Scoped by client as well as company: a company id alone must never reach another tenant's row. */
    Optional<PartnerCompanyXtrmAccount> findByClientIdAndPartnerCompanyId(UUID clientId, UUID partnerCompanyId);
}
