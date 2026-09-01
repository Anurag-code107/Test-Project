package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BreachIncident;
import com.tenxengage.app.entity.enums.BreachStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BreachIncidentRepository extends JpaRepository<BreachIncident, UUID> {

    List<BreachIncident> findByStatusNot(BreachStatus status);
}
