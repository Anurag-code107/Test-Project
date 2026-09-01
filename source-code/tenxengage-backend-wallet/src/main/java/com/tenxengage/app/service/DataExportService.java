package com.tenxengage.app.service;

import com.tenxengage.app.entity.ConsentRecord;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserLegalAcceptance;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ConsentRecordRepository;
import com.tenxengage.app.repository.NotificationRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

    private final UserRepository userRepository;
    private final UserLegalAcceptanceRepository userLegalAcceptanceRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final RewardBalanceRepository rewardBalanceRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    public DataExportService(UserRepository userRepository,
                             UserLegalAcceptanceRepository userLegalAcceptanceRepository,
                             ConsentRecordRepository consentRecordRepository,
                             RewardBalanceRepository rewardBalanceRepository,
                             NotificationRepository notificationRepository,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userLegalAcceptanceRepository = userLegalAcceptanceRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.rewardBalanceRepository = rewardBalanceRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Exports all personal data held for a user, structured for GDPR data portability (Article 20).
     * Returns a map that the controller serializes to JSON.
     *
     * @param userId   the ID of the user whose data to export
     * @param clientId the client the user must belong to (tenant isolation)
     * @return structured map of the user's personal data
     * @throws ResourceNotFoundException if the user does not exist within the client
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId, UUID clientId) {
        User user = userRepository.findByIdAndClientId(userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        log.info("Exporting user data for userId={} clientId={}", userId, clientId);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", java.time.Instant.now().toString());
        export.put("profile", buildProfileSection(user));
        export.put("legalAcceptances", buildLegalAcceptancesSection(userId));
        export.put("consentRecords", buildConsentRecordsSection(userId));
        export.put("rewardBalances", buildRewardBalancesSection(clientId, userId));
        export.put("notificationCount", notificationRepository.countByClientIdAndUserId(clientId, userId));

        // Log the export action for audit trail
        auditLogService.log(
                AuditAction.DATA_EXPORTED,
                AuditResourceType.USER,
                userId,
                user.getFirstName() + " " + user.getLastName(),
                "User data exported (GDPR data portability request)",
                null
        );

        return export;
    }

    private Map<String, Object> buildProfileSection(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", user.getEmail());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("phone", user.getPhone());
        profile.put("countryCode", user.getCountryCode());
        profile.put("status", user.getStatus().name());
        profile.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        profile.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        profile.put("onboardingCompletedAt",
                user.getOnboardingCompletedAt() != null ? user.getOnboardingCompletedAt().toString() : null);
        return profile;
    }

    private List<Map<String, Object>> buildLegalAcceptancesSection(UUID userId) {
        List<UserLegalAcceptance> acceptances = userLegalAcceptanceRepository.findByUserId(userId);
        return acceptances.stream()
                .map(a -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", a.getId().toString());
                    entry.put("policyId", a.getPolicyId().toString());
                    entry.put("acceptedAt", a.getAcceptedAt() != null ? a.getAcceptedAt().toString() : null);
                    entry.put("ipAddress", a.getIpAddress());
                    return entry;
                })
                .toList();
    }

    private List<Map<String, Object>> buildConsentRecordsSection(UUID userId) {
        List<ConsentRecord> records = consentRecordRepository.findByUserId(userId);
        return records.stream()
                .map(r -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", r.getId().toString());
                    entry.put("consentType", r.getConsentType().name());
                    entry.put("granted", r.isGranted());
                    entry.put("recordedAt", r.getRecordedAt() != null ? r.getRecordedAt().toString() : null);
                    entry.put("ipAddress", r.getIpAddress());
                    return entry;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRewardBalancesSection(UUID clientId, UUID userId) {
        List<RewardBalance> balances = rewardBalanceRepository.findByClientIdAndUserId(clientId, userId);
        return balances.stream()
                .map(b -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", b.getId().toString());
                    entry.put("currencyId", b.getCurrencyId());
                    entry.put("balance", b.getBalance().toPlainString());
                    return entry;
                })
                .toList();
    }
}
