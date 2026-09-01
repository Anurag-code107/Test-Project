package com.tenxengage.app.repository.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerLinkedCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PartnerLinkedCard}. Tenant-scoped by {@code clientId}; soft-deleted rows are
 * excluded automatically by the entity's {@code @SQLRestriction("deleted = false")}.
 */
@Repository
public interface PartnerLinkedCardRepository extends JpaRepository<PartnerLinkedCard, UUID> {

    /** All active cards for a user, oldest first — the deterministic auto-promote order. */
    List<PartnerLinkedCard> findByUserIdAndClientIdOrderByCreatedAtAsc(UUID userId, UUID clientId);

    /** Resolve a single card by our PK, tenant + self scoped. */
    Optional<PartnerLinkedCard> findByIdAndUserIdAndClientId(UUID id, UUID userId, UUID clientId);

    /** Belt-and-suspenders duplicate pre-check. */
    boolean existsByUserIdAndClientIdAndCardToken(UUID userId, UUID clientId, String cardToken);
}
