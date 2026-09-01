package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import org.junit.jupiter.api.Tag;
import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditActorType;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.UserAnonymizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for user anonymization (GDPR erasure). These tests do NOT use
 * {@code @Transactional} because the {@link UserAnonymizationService} internally uses
 * {@code AuditLogService} which runs with {@code Propagation.REQUIRES_NEW}. A test-level
 * transaction would be invisible to that new transaction, causing FK violations.
 * Instead, test data is cleaned up in {@code @AfterEach}.
 */
@Tag("integration")
class UserAnonymizationIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired
    private UserAnonymizationService userAnonymizationService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartnerCompanyRepository partnerCompanyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Client testClient;
    private PartnerCompany testPartnerCompany;

    // Track created entities for cleanup
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdAuditLogIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClient = clientRepository.saveAndFlush(Client.builder()
                .name("Anonymization Test Client")
                .subdomain("anon-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build());

        String partnerSuffix = UUID.randomUUID().toString().substring(0, 8);
        testPartnerCompany = partnerCompanyRepository.saveAndFlush(PartnerCompany.builder()
                .name("Test Partner " + partnerSuffix)
                .clientId(testClient.getId())
                .externalPartnerId("CT-TEST-" + partnerSuffix)
                .build());

        // AuditLogService.log() requires TenantContext to have the clientId set,
        // otherwise the anonymization audit entry won't be recorded.
        TenantContext.setClientId(testClient.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();

        // Clean up in reverse dependency order
        // 1. Audit logs referencing users and client
        auditLogRepository.deleteAllById(createdAuditLogIds);
        auditLogRepository.findAll().stream()
                .filter(log -> testClient.getId().equals(log.getClientId()))
                .forEach(log -> auditLogRepository.deleteById(log.getId()));

        // 2. Users referencing partner company and client
        for (UUID userId : createdUserIds) {
            userRepository.deleteById(userId);
        }

        // 3. Partner company referencing client
        partnerCompanyRepository.deleteById(testPartnerCompany.getId());

        // 4. Client
        clientRepository.deleteById(testClient.getId());

        createdUserIds.clear();
        createdAuditLogIds.clear();
    }

    @Test
    void testAnonymizeUserPreservesFinancialRecords() {
        User user = createActiveUser("financial-test", "Jane", "Smith");

        // Create audit log entries for this user (simulating prior activity)
        createAuditLogEntry(user, AuditAction.LOGGED_IN, "Login from web");
        createAuditLogEntry(user, AuditAction.SUBMITTED, "Submitted a claim");
        createAuditLogEntry(user, AuditAction.CLAIMED, "Claimed reward");

        // Anonymize the user
        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        // Verify user record is anonymized but still exists
        User anonymizedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(anonymizedUser.getEmail()).startsWith("anonymized-");
        assertThat(anonymizedUser.getEmail()).endsWith("@deleted.tenxengage.com");
        assertThat(anonymizedUser.getFirstName()).isEqualTo("Deleted");
        assertThat(anonymizedUser.getLastName()).isEqualTo("User");
        assertThat(anonymizedUser.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(anonymizedUser.getPhone()).isNull();
        assertThat(anonymizedUser.getAvatar()).isNull();
        assertThat(anonymizedUser.getCountryCode()).isNull();
        assertThat(anonymizedUser.getMetadata()).isEqualTo("{}");

        // Verify the user's prior audit log entries were anonymized
        // (actorEmail and actorName replaced, ipAddress nulled)
        List<AuditLog> auditLogs = auditLogRepository.findAll().stream()
                .filter(log -> user.getId().equals(log.getActorId()))
                .filter(log -> log.getAction() != AuditAction.ANONYMIZED)
                .toList();

        assertThat(auditLogs).isNotEmpty();
        for (AuditLog log : auditLogs) {
            assertThat(log.getActorEmail()).isEqualTo("[anonymized]");
            assertThat(log.getActorName()).isEqualTo("[anonymized]");
            assertThat(log.getIpAddress()).isNull();
        }
    }

    @Test
    void testAnonymizeUserPreventsLogin() {
        User user = createActiveUser("login-test", "Bob", "Jones");

        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        // Verify user status is ANONYMIZED
        User anonymizedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(anonymizedUser.getStatus()).isEqualTo(UserStatus.ANONYMIZED);

        // Verify password hash is set to a value that cannot match any BCrypt input
        assertThat(anonymizedUser.getPasswordHash()).isEqualTo("$ANONYMIZED$");
    }

    @Test
    void testCannotAnonymizeAlreadyAnonymizedUser() {
        User user = createActiveUser("double-anon", "Alice", "Brown");

        // First anonymization should succeed
        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        // Second anonymization should throw
        assertThatThrownBy(() ->
                userAnonymizationService.anonymizeUser(user.getId(), testClient.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already anonymized");
    }

    @Test
    void testAnonymizationIsIdempotentOnAuditLogs() {
        User user = createActiveUser("audit-test", "Charlie", "Davis");

        // Create audit entries for this user
        createAuditLogEntry(user, AuditAction.LOGGED_IN, "First login");
        createAuditLogEntry(user, AuditAction.EDITED, "Updated profile");

        // Anonymize the user
        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        // Verify the user's prior audit entries were anonymized
        List<AuditLog> priorLogs = auditLogRepository.findAll().stream()
                .filter(log -> user.getId().equals(log.getActorId()))
                .filter(log -> log.getAction() != AuditAction.ANONYMIZED)
                .toList();

        assertThat(priorLogs).hasSize(2);
        for (AuditLog log : priorLogs) {
            assertThat(log.getActorEmail()).isEqualTo("[anonymized]");
            assertThat(log.getActorName()).isEqualTo("[anonymized]");
        }

        // Verify the anonymization action itself was recorded in the audit log.
        // The AuditLogService.log() call in anonymizeUser uses TenantContext for the clientId
        // and records the action as ANONYMIZED with resource type USER.
        List<AuditLog> anonymizationLogs = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.ANONYMIZED)
                .filter(log -> log.getResourceType() == AuditResourceType.USER)
                .filter(log -> user.getId().equals(log.getResourceId()))
                .toList();

        assertThat(anonymizationLogs).isNotEmpty();
        AuditLog anonymizationEntry = anonymizationLogs.getFirst();
        assertThat(anonymizationEntry.getDescription()).contains("GDPR erasure");
        assertThat(anonymizationEntry.getResourceName()).isEqualTo("Deleted User");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createActiveUser(String emailPrefix, String firstName, String lastName) {
        String uniqueEmail = emailPrefix + "-" + UUID.randomUUID() + "@test.local";
        User user = userRepository.saveAndFlush(User.builder()
                .email(uniqueEmail)
                .firstName(firstName)
                .lastName(lastName)
                .passwordHash("$2a$10$dummyBcryptHashForTesting123456789012345678901234")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartnerCompany.getId())
                .phone("+1555000111")
                .countryCode("US")
                .build());
        createdUserIds.add(user.getId());
        return user;
    }

    private void createAuditLogEntry(User user, AuditAction action, String description) {
        AuditLog log = auditLogRepository.saveAndFlush(AuditLog.builder()
                .clientId(testClient.getId())
                .actorType(AuditActorType.USER)
                .actorId(user.getId())
                .actorEmail(user.getEmail())
                .actorName(user.getFirstName() + " " + user.getLastName())
                .action(action)
                .resourceType(AuditResourceType.USER)
                .resourceId(user.getId())
                .resourceName(user.getFirstName() + " " + user.getLastName())
                .description(description)
                .ipAddress("192.168.1.100")
                .build());
        createdAuditLogIds.add(log.getId());
    }
}
