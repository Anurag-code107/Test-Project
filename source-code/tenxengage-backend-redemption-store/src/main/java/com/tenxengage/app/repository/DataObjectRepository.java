package com.tenxengage.app.repository;

import com.tenxengage.app.entity.DataObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataObjectRepository extends JpaRepository<DataObject, UUID> {

    Optional<DataObject> findByIdAndClientId(UUID id, UUID clientId);

    List<DataObject> findByClientIdOrderBySortOrder(UUID clientId);

    boolean existsByClientIdAndName(UUID clientId, String name);

    Optional<DataObject> findByClientIdAndName(UUID clientId, String name);
}
