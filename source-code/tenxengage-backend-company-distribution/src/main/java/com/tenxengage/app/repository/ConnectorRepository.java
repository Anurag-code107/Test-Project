package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Connector;
import com.tenxengage.app.entity.enums.ConnectorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, UUID> {

    List<Connector> findByClientIdOrderByName(UUID clientId);

    List<Connector> findByClientIdAndConnectorType(UUID clientId, ConnectorType connectorType);

    boolean existsByClientIdAndName(UUID clientId, String name);
}
