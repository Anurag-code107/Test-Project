package com.tenxengage.app.integration.redemption;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.service.redemption.RedemptionAnalyticsService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — Audit row verification for analytics operations.
 *
 * Verifies that the export service writes exactly one audit row per successful
 * download (T-11a), that the read-only analytics summary writes no audit row
 * (T-11b), and that a 403-path call never reaches the export service layer
 * (permission check happens in the controller — T-11c is validated by the
 * controller unit test RedemptionAnalyticsControllerTest#EXPORT_403).
 *
 * Covers: T-11a, T-11b from test-plan.md.
 */
@Tag("integration")
class RedemptionAnalyticsAuditIT extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAnalyticsService analyticsService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    private Client testClient;
    private User testUser;
    private RewardWallet testWallet;

    @BeforeEach
    void setUp() {
        // logAsync uses @Async — inherit security context into the thread pool thread
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testUser = userRepository.save(UserFixtures.activeUser(testClient.getId(), null).build());
        testWallet = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(testClient.getId(), testUser.getId())
                        .currencyId("cash")
                        .build());
        setSecurityContext(testUser);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.findByActorIdAndResourceType(
                testUser.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT
        ).forEach(auditLogRepository::delete);
        rewardWalletRepository.delete(testWallet);
        userRepository.delete(testUser);
        clientRepository.delete(testClient);
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    /**
     * T-11a: Export → 200 → audit row written with correct action/resourceType/actorId.
     */
    @Test
    void export_writesAuditRow_withCorrectFields() {
        long countBefore = auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);

        analyticsService.exportUnredeemedBalances();
        // logAsync is @Async — poll until the executor thread commits the audit row
        await().atMost(3, SECONDS).until(() ->
                auditLogRepository.countByClientIdAndResourceType(
                        testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT) >= countBefore + 1);

        long countAfter = auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);
        assertThat(countAfter).as("Exactly one audit row should be written").isEqualTo(countBefore + 1);

        List<AuditLog> rows = auditLogRepository.findByActorIdAndResourceType(
                testUser.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.DATA_EXPORTED);
        assertThat(row.getResourceType()).isEqualTo(AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);
        assertThat(row.getActorId()).isEqualTo(testUser.getId());
        assertThat(row.getClientId()).isEqualTo(testClient.getId());
    }

    /**
     * T-11b: GET analytics summary → 200 → audit table unchanged (read-only, no audit row).
     */
    @Test
    void analyticsSummary_doesNotWriteAuditRow() {
        long countBefore = auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);

        LocalDate today = LocalDate.now();
        analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        long countAfter = auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);
        assertThat(countAfter).as("Read-only analytics summary must not write audit rows")
                .isEqualTo(countBefore);
    }

    /**
     * T-11c (controller layer): Export → 403 → audit table unchanged.
     * Permission check is enforced by @RequiresPermission on the controller before the
     * service method is reached. Covered by RedemptionAnalyticsControllerTest#EXPORT_403.
     * This service-layer test verifies that no audit row exists before the export is called.
     */
    @Test
    void beforeExport_noAuditRowExists_forFreshUser() {
        long count = auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REDEMPTION_ANALYTICS_EXPORT);
        assertThat(count).as("No prior audit rows should exist for a fresh test tenant").isEqualTo(0);
    }

    /**
     * T-11a (CSV): Export returns UTF-8 CSV with the correct header and at least one data row
     * containing the test wallet's userId and currencyType.
     */
    @Test
    void export_csvContent_containsDataRowForSeededWallet() {
        byte[] csv = analyticsService.exportUnredeemedBalances();
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).startsWith(
                "userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance\n");

        String dataRows = content.substring(content.indexOf('\n') + 1);
        assertThat(dataRows.strip()).as("At least one data row expected for seeded wallet").isNotEmpty();
        assertThat(dataRows).contains(testUser.getId().toString());
        assertThat(dataRows).contains(",cash,");       // currencyType field
        assertThat(dataRows).contains(",Individual,"); // individual wallet companyName
    }

    /**
     * T-11a (empty): Export for a tenant with no wallets returns only the header row.
     */
    @Test
    void export_emptyTenant_returnsHeaderOnly() {
        Client emptyClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        User emptyUser = userRepository.save(UserFixtures.activeUser(emptyClient.getId(), null).build());
        setSecurityContext(emptyUser);
        try {
            byte[] csv = analyticsService.exportUnredeemedBalances();
            String content = new String(csv, StandardCharsets.UTF_8);
            String[] lines = content.stripTrailing().split("\n");

            assertThat(lines).hasSize(1);
            assertThat(lines[0]).startsWith("userId,userName");
        } finally {
            setSecurityContext(testUser);
            userRepository.delete(emptyUser);
            clientRepository.delete(emptyClient);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void setSecurityContext(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
