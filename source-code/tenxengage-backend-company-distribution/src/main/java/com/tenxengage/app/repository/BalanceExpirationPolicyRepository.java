package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BalanceExpirationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceExpirationPolicyRepository extends JpaRepository<BalanceExpirationPolicy, UUID> {

    List<BalanceExpirationPolicy> findByClientId(UUID clientId);

    Optional<BalanceExpirationPolicy> findByClientIdAndCurrencyId(UUID clientId, String currencyId);
}
