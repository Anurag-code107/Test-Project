package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ConnectorFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectorFieldMappingRepository extends JpaRepository<ConnectorFieldMapping, UUID> {

    List<ConnectorFieldMapping> findByDataObjectId(UUID dataObjectId);

    void deleteByDataObjectId(UUID dataObjectId);
}
