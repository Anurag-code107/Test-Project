package com.tenxengage.app.performance;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import org.junit.jupiter.api.Tag;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ComplianceValueCap;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ComplianceValueCapRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.ComplianceCapValidator;
import com.tenxengage.app.service.DataRetentionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance-aware integration tests that seed bulk data and measure execution time.
 * These tests run against a local PostgreSQL instance (docker compose) and are
 * gated by database availability.
 */
@Transactional
@Tag("performance")
class CompliancePerformanceTest extends AbstractLocalIntegrationTest {

    @Autowired
    private ComplianceCapValidator complianceCapValidator;

    @Autowired
    private DataRetentionService dataRetentionService;

    @Autowired
    private RewardTransactionRepository rewardTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PartnerCompanyRepository partnerCompanyRepository;

    @Autowired
    private ComplianceValueCapRepository complianceValueCapRepository;

    @Autowired
    private IncentiveRepository incentiveRepository;

    @Autowired
    private EntityManager entityManager;

    private Client testClient;
    private User testUser;
    private Incentive testIncentive;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(Client.builder()
                .name("Performance Test Client")
                .subdomain("perf-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build());

        String partnerSuffix = UUID.randomUUID().toString().substring(0, 8);
        PartnerCompany testPartner = partnerCompanyRepository.save(PartnerCompany.builder()
                .name("Perf Test Partner " + partnerSuffix)
                .clientId(testClient.getId())
                .externalPartnerId("CT-PERF-" + partnerSuffix)
                .build());

        testUser = userRepository.save(User.builder()
                .email("perf-user-" + UUID.randomUUID() + "@test.com")
                .firstName("Perf")
                .lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartner.getId())
                .countryCode("DE")
                .build());

        testIncentive = incentiveRepository.save(Incentive.builder()
                .name("Performance Test Incentive")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.ACTIVE)
                .clientId(testClient.getId())
                .createdBy(testUser.getId())
                .build());
    }

    @Test
    void capValidation_performsUnder100ms_withLargeTransactionHistory() {
        // Seed 1000 reward transactions for the test user
        seedRewardTransactions(1000);

        // Seed a client-specific compliance value cap for Germany (avoids conflict
        // with the baseline-seeded system-default DE cap).
        complianceValueCapRepository.save(ComplianceValueCap.builder()
                .countryCode("DE")
                .annualCapAmount(new BigDecimal("50000.00"))
                .annualCapCurrency("EUR")
                .enhancedApprovalThreshold(new BigDecimal("10000.00"))
                .clientId(testClient.getId())
                .build());

        entityManager.flush();
        entityManager.clear();

        // Time 100 validation calls against the large transaction history
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            complianceCapValidator.validateClaim(
                    testUser.getId(), testClient.getId(), "DE", new BigDecimal("10.00"));
        }
        long elapsed = System.currentTimeMillis() - start;

        // 100 calls should complete under 10 seconds (100ms average per call)
        assertThat(elapsed)
                .as("100 cap validation calls with 1000 transaction history should complete under 10s, took %dms",
                        elapsed)
                .isLessThan(10000);
    }

    @Test
    void retentionPolicyQuery_performsAcceptably_withCleanDatabase() {
        // The retention service queries system defaults and client overrides.
        // In a clean test DB, this exercises the query path with no data (fast path).
        // We verify it completes quickly and returns a stable result.
        entityManager.flush();
        entityManager.clear();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            dataRetentionService.getRetentionPolicies(testClient.getId());
        }
        long elapsed = System.currentTimeMillis() - start;

        // 100 calls to getRetentionPolicies should complete under 5 seconds
        assertThat(elapsed)
                .as("100 retention policy lookups should complete under 5s, took %dms", elapsed)
                .isLessThan(5000);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void seedRewardTransactions(int count) {
        List<RewardTransaction> transactions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            transactions.add(RewardTransaction.builder()
                    .clientId(testClient.getId())
                    .userId(testUser.getId())
                    .incentiveId(testIncentive.getId())
                    .currencyId("EUR")
                    .amountPotential(new BigDecimal("25.00"))
                    .amountAwarded(new BigDecimal("25.00"))
                    .budgetCapped(false)
                    .build());
        }
        rewardTransactionRepository.saveAllAndFlush(transactions);
    }
}
