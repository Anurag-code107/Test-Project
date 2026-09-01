package com.tenxengage.app.repository;

import com.tenxengage.app.entity.DataObjectField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DataObjectFieldRepository extends JpaRepository<DataObjectField, UUID> {

    List<DataObjectField> findByDataObjectIdOrderBySortOrder(UUID dataObjectId);

    boolean existsByDataObjectIdAndName(UUID dataObjectId, String name);

    @Query("SELECT f FROM DataObjectField f JOIN f.dataObject o " +
           "WHERE o.clientId = :clientId AND f.excludeFromRules = false AND f.ruleLabel IS NOT NULL " +
           "ORDER BY o.sortOrder, f.sortOrder")
    List<DataObjectField> findRuleEligibleFieldsByClientId(UUID clientId);

    @Query("SELECT f FROM DataObjectField f JOIN f.dataObject o " +
           "WHERE o.clientId = :clientId AND o.id = :dataObjectId " +
           "AND f.excludeFromRules = false AND f.ruleLabel IS NOT NULL " +
           "ORDER BY f.sortOrder")
    List<DataObjectField> findRuleEligibleFieldsByClientIdAndDataObjectId(UUID clientId, UUID dataObjectId);

    @Query("SELECT f FROM DataObjectField f JOIN f.dataObject o " +
           "WHERE o.clientId = :clientId AND o.name = :dataObjectName " +
           "AND f.excludeFromRules = false AND f.ruleLabel IS NOT NULL " +
           "ORDER BY f.sortOrder")
    List<DataObjectField> findRuleEligibleFieldsByClientIdAndDataObjectName(UUID clientId, String dataObjectName);
}
