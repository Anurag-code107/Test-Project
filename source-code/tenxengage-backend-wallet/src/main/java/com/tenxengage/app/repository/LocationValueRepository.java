package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LocationValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationValueRepository extends JpaRepository<LocationValue, UUID> {

    List<LocationValue> findByClientIdAndLevelIdAndParentIdIsNullOrderByName(UUID clientId, UUID levelId);

    List<LocationValue> findByClientIdAndLevelId(UUID clientId, UUID levelId);

    List<LocationValue> findByIdIn(List<UUID> ids);

    boolean existsByClientIdAndLevelIdAndParentIdAndName(UUID clientId, UUID levelId, UUID parentId, String name);

    // Case-sensitive resolver used by the audience-rule save path. (clientId, levelId, name) is
    // de-facto unique for the seeded hierarchy and enforced at the root by
    // idx_location_values_root_unique. Names that match exactly fall through to the canonical
    // LocationValue UUID; misses bubble up as an empty Optional and the caller rejects with 400.
    Optional<LocationValue> findByClientIdAndLevelIdAndName(UUID clientId, UUID levelId, String name);
}
