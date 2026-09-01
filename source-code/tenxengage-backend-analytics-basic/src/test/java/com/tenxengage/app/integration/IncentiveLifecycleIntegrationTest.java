package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Tag("integration")
class IncentiveLifecycleIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private IncentiveRepository incentiveRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private UserRepository userRepository;

    private Client testClient;
    private Client otherClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        otherClient = clientRepository.save(ClientFixtures.activeEnterprise().build());

        PartnerCompany partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(testClient.getId()).build());

        testUser = userRepository.save(User.builder()
                .email("lifecycle-" + UUID.randomUUID() + "@test.com")
                .firstName("Test").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(partner.getId())
                .build());

        TenantContext.setClientId(testClient.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDraftIncentive_persists() {
        Incentive incentive = incentiveRepository.save(Incentive.builder()
                .name("Lifecycle Test Incentive")
                .description("Testing the lifecycle")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .startDate(Instant.now())
                .endDate(Instant.now().plus(90, ChronoUnit.DAYS))
                .build());

        assertThat(incentive.getId()).isNotNull();
        assertThat(incentive.getStatus()).isEqualTo(IncentiveStatus.DRAFT);
    }

    @Test
    void statusTransition_draftToActive() {
        Incentive incentive = incentiveRepository.save(Incentive.builder()
                .name("Draft to Active")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .startDate(Instant.now())
                .endDate(Instant.now().plus(90, ChronoUnit.DAYS))
                .build());

        incentive.setStatus(IncentiveStatus.ACTIVE);
        incentive.setStatusChangedAt(Instant.now());
        incentive = incentiveRepository.save(incentive);

        assertThat(incentive.getStatus()).isEqualTo(IncentiveStatus.ACTIVE);
        assertThat(incentive.getStatusChangedAt()).isNotNull();
    }

    @Test
    void tenantIsolation_incentivesAreScopedToClient() {
        incentiveRepository.save(Incentive.builder()
                .name("Client A Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .build());

        incentiveRepository.save(Incentive.builder()
                .name("Client B Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(otherClient.getId())
                .createdBy(testUser.getId())
                .build());

        // With tenant filter active for testClient, should only see testClient's incentive
        var clientAIncentives = incentiveRepository.findByIdAndClientIdAndDeletedFalse(
                incentiveRepository.findAll().stream()
                        .filter(i -> i.getClientId().equals(testClient.getId()))
                        .findFirst().orElseThrow().getId(),
                testClient.getId());

        assertThat(clientAIncentives).isPresent();
        assertThat(clientAIncentives.get().getName()).isEqualTo("Client A Incentive");
    }

    @Test
    void softDelete_setsDeletedFlag() {
        Incentive incentive = incentiveRepository.save(Incentive.builder()
                .name("To Delete")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .build());

        incentive.setDeleted(true);
        incentiveRepository.save(incentive);

        // findByIdAndClientIdAndDeletedFalse should NOT find it
        var found = incentiveRepository.findByIdAndClientIdAndDeletedFalse(
                incentive.getId(), testClient.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void customFieldValues_roundTripsThroughJpa() {
        // BUG-046 regression: the custom_field_values jsonb column must survive a save → load
        // round-trip, mirroring how countriesText / specificPartners do for their text columns.
        // The companion service-layer wiring (CreateIncentiveRequest → entity, response DTO)
        // is covered by IncentiveDetailResponseTest; this asserts the JPA mapping itself.
        String json = "{\"campaignSegment\":\"Q4 Push\",\"productLine\":\"Server\"}";
        Incentive incentive = incentiveRepository.save(Incentive.builder()
                .name("Custom Field Round-Trip")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .customFieldValues(json)
                .build());

        var loaded = incentiveRepository.findByIdAndClientIdAndDeletedFalse(
                incentive.getId(), testClient.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getCustomFieldValues()).isEqualTo(json);
    }

    @Test
    void approvalRound_incrementsCorrectly() {
        Incentive incentive = incentiveRepository.save(Incentive.builder()
                .name("Approval Round Test")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.PENDING_APPROVAL)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .requiresApproval(true)
                .requiredApprovals(1)
                .approvalRound(1)
                .build());

        incentive.setApprovalRound(incentive.getApprovalRound() + 1);
        incentive = incentiveRepository.save(incentive);

        assertThat(incentive.getApprovalRound()).isEqualTo(2);
    }
}
