package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerProgramAcknowledgment;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerProgramAcknowledgmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PartnerAcknowledgmentService {

    private static final Logger log = LoggerFactory.getLogger(PartnerAcknowledgmentService.class);

    private final PartnerProgramAcknowledgmentRepository acknowledgmentRepository;

    public PartnerAcknowledgmentService(PartnerProgramAcknowledgmentRepository acknowledgmentRepository) {
        this.acknowledgmentRepository = acknowledgmentRepository;
    }

    /**
     * Records that a partner company has acknowledged the anti-bribery policy for a specific incentive.
     * Prevents duplicate acknowledgments for the same partner-incentive pair.
     */
    @Transactional
    public PartnerProgramAcknowledgment acknowledgeProgram(UUID partnerCompanyId, UUID incentiveId,
                                                           UUID acknowledgedByUserId, UUID clientId) {
        if (acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId)) {
            throw new BusinessRuleException(
                    "Partner company " + partnerCompanyId + " has already acknowledged incentive " + incentiveId);
        }

        PartnerProgramAcknowledgment acknowledgment = PartnerProgramAcknowledgment.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .incentiveId(incentiveId)
                .acknowledgedBy(acknowledgedByUserId)
                .acknowledgedAt(Instant.now())
                .policyVersion("1.0")
                .build();

        PartnerProgramAcknowledgment saved = acknowledgmentRepository.save(acknowledgment);
        log.info("Partner acknowledgment recorded: partnerCompanyId={}, incentiveId={}, acknowledgedBy={}",
                partnerCompanyId, incentiveId, acknowledgedByUserId);
        return saved;
    }

    /**
     * Checks whether a partner company has acknowledged the anti-bribery policy for a given incentive.
     */
    @Transactional(readOnly = true)
    public boolean isAcknowledged(UUID partnerCompanyId, UUID incentiveId) {
        return acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId);
    }

    /**
     * Returns all acknowledgments associated with a specific incentive.
     */
    @Transactional(readOnly = true)
    public List<PartnerProgramAcknowledgment> getAcknowledgmentsForIncentive(UUID incentiveId) {
        return acknowledgmentRepository.findByIncentiveId(incentiveId);
    }

    /**
     * Returns all acknowledgments associated with a specific partner company.
     */
    @Transactional(readOnly = true)
    public List<PartnerProgramAcknowledgment> getAcknowledgmentsForPartner(UUID partnerCompanyId) {
        return acknowledgmentRepository.findByPartnerCompanyId(partnerCompanyId);
    }
}
