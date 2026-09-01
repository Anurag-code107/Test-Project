package com.tenxengage.app.service;

import com.tenxengage.app.entity.GovernmentSegmentConfig;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.enums.GovernmentDealRestrictionMode;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.GovernmentSegmentConfigRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GovernmentDealService {

    private static final Logger log = LoggerFactory.getLogger(GovernmentDealService.class);

    private final GovernmentSegmentConfigRepository governmentSegmentConfigRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;

    public GovernmentDealService(GovernmentSegmentConfigRepository governmentSegmentConfigRepository,
                                 PartnerCompanyRepository partnerCompanyRepository) {
        this.governmentSegmentConfigRepository = governmentSegmentConfigRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
    }

    /**
     * Determines whether a deal is classified as a government deal based on
     * the customer segment value and the tenant's government segment configuration.
     */
    @Transactional(readOnly = true)
    public boolean isGovernmentDeal(UUID clientId, String customerSegment) {
        if (customerSegment == null || customerSegment.isBlank()) {
            return false;
        }
        return governmentSegmentConfigRepository.findByClientIdAndSegmentValue(clientId, customerSegment)
                .map(GovernmentSegmentConfig::isGovernment)
                .orElse(false);
    }

    /**
     * Returns the government deal restriction mode configured for a partner company.
     */
    @Transactional(readOnly = true)
    public GovernmentDealRestrictionMode getRestrictionMode(UUID partnerCompanyId) {
        PartnerCompany partner = partnerCompanyRepository.findById(partnerCompanyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Partner company not found: " + partnerCompanyId));
        GovernmentDealRestrictionMode mode = partner.getGovernmentDealRestrictionMode();
        return mode != null ? mode : GovernmentDealRestrictionMode.NONE;
    }

    /**
     * Returns the list of segment values classified as government for the given tenant.
     */
    @Transactional(readOnly = true)
    public List<String> getGovernmentSegments(UUID clientId) {
        return governmentSegmentConfigRepository.findByClientId(clientId).stream()
                .filter(GovernmentSegmentConfig::isGovernment)
                .map(GovernmentSegmentConfig::getSegmentValue)
                .toList();
    }

    /**
     * Replaces the government segment configuration for a tenant.
     * Deletes existing entries and inserts the provided segment values.
     */
    @Transactional
    public List<GovernmentSegmentConfig> updateGovernmentSegments(UUID clientId, List<String> segmentValues) {
        List<GovernmentSegmentConfig> existing = governmentSegmentConfigRepository.findByClientId(clientId);
        governmentSegmentConfigRepository.deleteAll(existing);

        List<GovernmentSegmentConfig> newConfigs = segmentValues.stream()
                .map(value -> GovernmentSegmentConfig.builder()
                        .clientId(clientId)
                        .segmentValue(value)
                        .isGovernment(true)
                        .build())
                .toList();

        List<GovernmentSegmentConfig> saved = governmentSegmentConfigRepository.saveAll(newConfigs);
        log.info("Government segments updated: clientId={}, segments={}", clientId, segmentValues);
        return saved;
    }
}
