package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ApprovalDecisionEntity;
import com.tenxengage.app.entity.enums.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecisionEntity, UUID> {

    Optional<ApprovalDecisionEntity> findByIncentiveIdAndApproverEmail(UUID incentiveId, String approverEmail);

    Optional<ApprovalDecisionEntity> findByTokenId(UUID tokenId);

    long countByIncentiveIdAndDecision(UUID incentiveId, ApprovalDecision decision);

    long countByIncentiveId(UUID incentiveId);

    List<ApprovalDecisionEntity> findAllByIncentiveId(UUID incentiveId);
}
