package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.enums.CurrencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {

    List<Currency> findByClientIdOrderByTypeAscCodeAsc(UUID clientId);

    Optional<Currency> findByClientIdAndCode(UUID clientId, String code);

    boolean existsByClientIdAndCode(UUID clientId, String code);

    List<Currency> findByClientIdAndType(UUID clientId, CurrencyType type);
}
