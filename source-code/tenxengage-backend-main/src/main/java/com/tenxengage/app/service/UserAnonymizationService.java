package com.tenxengage.app.service;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserAnonymizationService {

    private static final Logger log = LoggerFactory.getLogger(UserAnonymizationService.class);

    private static final String ANONYMIZED_EMAIL_DOMAIN = "@deleted.tenxengage.com";
    private static final String ANONYMIZED_FIRST_NAME = "Deleted";
    private static final String ANONYMIZED_LAST_NAME = "User";
    private static final String ANONYMIZED_PASSWORD_HASH = "$ANONYMIZED$";
    private static final String ANONYMIZED_METADATA = "{}";

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    public UserAnonymizationService(UserRepository userRepository,
                                    AuditLogRepository auditLogRepository,
                                    AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Anonymizes a user's personal data in compliance with GDPR right-to-erasure.
     * Replaces PII with placeholder values and anonymizes related audit log entries.
     * This is a soft-delete: the user record is preserved for referential integrity,
     * but all identifying information is removed.
     *
     * @param userId   the ID of the user to anonymize
     * @param clientId the client the user must belong to (tenant isolation)
     * @throws ResourceNotFoundException if the user does not exist within the client
     * @throws BusinessRuleException     if the user is already anonymized or is an admin
     */
    @Transactional
    public void anonymizeUser(UUID userId, UUID clientId) {
        User user = userRepository.findByIdAndClientId(userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        validateAnonymizable(user);

        String originalEmail = user.getEmail();

        // Anonymize user profile fields
        String anonymizedEmail = "anonymized-" + UUID.randomUUID() + ANONYMIZED_EMAIL_DOMAIN;
        user.setEmail(anonymizedEmail);
        user.setFirstName(ANONYMIZED_FIRST_NAME);
        user.setLastName(ANONYMIZED_LAST_NAME);
        user.setPhone(null);
        user.setAvatar(null);
        user.setPasswordHash(ANONYMIZED_PASSWORD_HASH);
        user.setStatus(UserStatus.ANONYMIZED);
        user.setMetadata(ANONYMIZED_METADATA);
        user.setCountryCode(null);

        userRepository.save(user);

        // Anonymize audit log entries associated with this user
        int anonymizedLogs = auditLogRepository.anonymizeByActorId(userId);
        log.info("Anonymized user userId={} clientId={}, scrubbed {} audit log entries",
                userId, clientId, anonymizedLogs);

        // Record the anonymization action itself (using the admin's context, not the anonymized user)
        auditLogService.log(
                AuditAction.ANONYMIZED,
                AuditResourceType.USER,
                userId,
                ANONYMIZED_FIRST_NAME + " " + ANONYMIZED_LAST_NAME,
                "User anonymized (GDPR erasure). Original email hash preserved in audit metadata.",
                null
        );
    }

    /**
     * Checks whether a user can be anonymized.
     *
     * @param userId the ID of the user to check
     * @return true if the user exists and is eligible for anonymization
     */
    @Transactional(readOnly = true)
    public boolean isAnonymizable(UUID userId) {
        return userRepository.findById(userId)
                .map(this::canAnonymize)
                .orElse(false);
    }

    /**
     * Validates that the user is eligible for anonymization, throwing if not.
     */
    private void validateAnonymizable(User user) {
        if (user.getStatus() == UserStatus.ANONYMIZED) {
            throw new BusinessRuleException("User is already anonymized");
        }

        if (hasAdminRole(user)) {
            throw new BusinessRuleException(
                    "Cannot anonymize users with admin roles (CLIENT_ADMIN or TENX_ADMIN). "
                    + "Remove admin roles before anonymization.");
        }
    }

    /**
     * Returns true if the user can be anonymized (not already anonymized, not an admin).
     */
    private boolean canAnonymize(User user) {
        if (user.getStatus() == UserStatus.ANONYMIZED) {
            return false;
        }
        return !hasAdminRole(user);
    }

    private boolean hasAdminRole(User user) {
        // TENX_ADMIN users have no clientId
        if (user.getClientId() == null) {
            return true;
        }
        // CLIENT_ADMIN is identified by the clientRole's baseRoleName
        if (user.getClientRole() != null
                && "CLIENT_ADMIN".equals(user.getClientRole().getBaseRoleName())) {
            return true;
        }
        return false;
    }
}
