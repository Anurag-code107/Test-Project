package com.tenxengage.app.service.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.UpsertBalanceExpirationPolicyRequest;
import com.tenxengage.app.dto.response.BalanceBreakageReportResponse;
import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ExpirationMode;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.entity.enums.Granularity;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.BalanceExpirationPolicyRepository;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.service.WalletService;
import com.tenxengage.app.testdata.ClientFixtures;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T1 cross-story integration tests for reward-balance-expiration — the full
 * configure → warn → expire → breakage chain against the real DB (Kafka up).
 *
 * <p>No class-level {@code @Transactional}: the batch's expire phase runs in its own
 * {@code @Transactional} (via self-proxy) and the warn phase auto-commits + publishes to Kafka,
 * so setup data must be committed first. Each test uses a fresh client (isolation) and cleans up
 * its rows in {@code @AfterEach} (FK order via JdbcTemplate).
 *
 * <p>Expire-phase tests use FIXED_DATE policies saved directly (a past/near date the batch acts on);
 * service-driven tests (cancel-on-relax, breakage, validation) go through the policy/report services
 * and are guarded by {@code assumeTrue(featureEnabled)} since those check the tenant feature flag.
 */
@Tag("integration")
class BalanceExpirationLifecycleIT extends AbstractLocalIntegrationTest {

    private static final String CURRENCY = "points";
    private static final String NOTIFICATION_TOPIC = "notification-events";

    @Autowired private BalanceExpiryBatchService batchService;
    @Autowired private BalanceExpirationPolicyService policyService;
    @Autowired private BalanceBreakageReportService breakageReportService;
    @Autowired private BalanceExpirationPolicyRepository policyRepository;
    @Autowired private BalanceExpiryNoticeRepository noticeRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRoleRepository clientRoleRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private WalletService walletService;
    @Autowired private FeatureFlagService featureFlagService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Value("${spring.kafka.bootstrap-servers}") private String kafkaBootstrapServers;

    private Client testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testUser = userRepository.save(User.builder()
                .email("bal-exp-" + UUID.randomUUID() + "@test.com")
                .firstName("Bal").lastName("Exp")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .build());
        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "CLIENT_ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        UUID c = testClient.getId();
        // FK order: notices → ledger → wallets → policies → users → partner_companies → client_roles → client
        // "users" and "partner_companies" are scoped to testClient; clean them up broadly so that tests
        // which create extra fixtures (e.g. companyWalletExpiry_notifiesOnlyOwningCompanyAdmins) leave no
        // orphaned rows even when they fail before their own explicit cleanup.
        jdbc.update("DELETE FROM balance_expiry_notices WHERE client_id = ?", c);
        jdbc.update("DELETE FROM ledger_entries WHERE client_id = ?", c);
        jdbc.update("DELETE FROM reward_wallets WHERE client_id = ?", c);
        jdbc.update("DELETE FROM balance_expiration_policies WHERE client_id = ?", c);
        jdbc.update("DELETE FROM audit_logs WHERE client_id = ?", c);
        jdbc.update("DELETE FROM users WHERE client_id = ?", c);
        jdbc.update("DELETE FROM partner_companies WHERE client_id = ?", c);
        jdbc.update("DELETE FROM client_roles WHERE client_id = ?", c);
        safeDelete(() -> clientRepository.delete(testClient));
    }

    // ── Full lifecycle: warn → expire (US-01/02/03) ───────────────────────────

    @Test
    void fullLifecycle_warnThenExpire_writesExpiryDebitAndReducesBalance() {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        int warned = batchService.processWarnPhaseForPolicy(policy);
        assertThat(warned).isEqualTo(1);

        BalanceExpiryNotice notice = notice(wallet.getId(), fixedDate);
        assertThat(notice.getStatus()).isEqualTo(ExpiryNoticeStatus.NOTIFIED);
        assertThat(notice.getNotifiedAt()).isNotNull();

        int expired = batchService.processExpirePhaseForPolicy(policy);
        assertThat(expired).isEqualTo(1);

        BalanceExpiryNotice afterExpiry = notice(wallet.getId(), fixedDate);
        assertThat(afterExpiry.getStatus()).isEqualTo(ExpiryNoticeStatus.EXPIRED);
        assertThat(afterExpiry.getExpiredAmount()).isEqualByComparingTo("100.00");
        assertThat(afterExpiry.getLedgerEntryId()).isNotNull();

        assertThat(rewardWalletRepository.findById(wallet.getId()).orElseThrow().getAvailableBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(expiryEntries(wallet.getId())).hasSize(1);
        assertThat(expiryEntries(wallet.getId()).get(0).getAmount()).isEqualByComparingTo("100.00");

        // AC-6 audit (real DB): the expire phase writes a synchronous EXPIRED / REWARD_WALLET system event.
        // REWARD_WALLET audits are written ONLY by the expiry path (BalanceExpiryBatchService), and the
        // client is fresh per test — so a non-zero count confirms the expiry audit was persisted.
        assertThat(auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.REWARD_WALLET)).isGreaterThanOrEqualTo(1);
    }

    // ── Idempotent expiry — no double debit on re-run (FR-09.8) ───────────────

    @Test
    void idempotentExpiry_secondSweep_noSecondDebit() {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        batchService.processWarnPhaseForPolicy(policy);
        assertThat(batchService.processExpirePhaseForPolicy(policy)).isEqualTo(1);
        // Second sweep over the now-EXPIRED notice
        int secondPass = batchService.processExpirePhaseForPolicy(policy);

        assertThat(secondPass).isEqualTo(0);
        assertThat(expiryEntries(wallet.getId())).hasSize(1);  // no double-debit
        assertThat(rewardWalletRepository.findById(wallet.getId()).orElseThrow().getAvailableBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Grace window — newly enabled policy warns nothing (FR-09.7) ───────────

    @Test
    void graceWindow_newlyEnabledPolicy_warnsNothing() {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        // enabled_at = now → grace window (leadTimeDays) NOT yet elapsed
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, Instant.now(), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        int warned = batchService.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(0);
        assertThat(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                testClient.getId(), wallet.getId(), CURRENCY, fixedDate)).isEmpty();
    }

    // ── Reserved balance protected — only availableBalance expires (ADR #3) ───

    @Test
    void reservedBalanceProtected_expiresOnlyAvailable() {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));
        walletService.reserve(wallet.getId(), new BigDecimal("30.00"), "TEST", UUID.randomUUID());

        batchService.processWarnPhaseForPolicy(policy);
        batchService.processExpirePhaseForPolicy(policy);

        RewardWallet after = rewardWalletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(after.getReservedBalance()).isEqualByComparingTo("30.00");  // untouched
        assertThat(expiryEntries(wallet.getId()).get(0).getAmount()).isEqualByComparingTo("70.00");
    }

    // ── Policy disabled after warn → expire skips (AC-4 flow-gap) ─────────────

    @Test
    void policyDisabledAfterWarn_expireSkips() {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        assertThat(batchService.processWarnPhaseForPolicy(policy)).isEqualTo(1);

        // Disable the policy after the warn phase, then run expire with the disabled policy.
        policy.setEnabled(false);
        policyRepository.save(policy);
        int expired = batchService.processExpirePhaseForPolicy(policy);

        assertThat(expired).isEqualTo(0);
        assertThat(expiryEntries(wallet.getId())).isEmpty();
        assertThat(rewardWalletRepository.findById(wallet.getId()).orElseThrow().getAvailableBalance())
                .isEqualByComparingTo("100.00");
    }

    // ── Cancel-on-relax — disabling cancels a NOTIFIED notice (FR-09.10) ──────

    @Test
    void cancelOnRelax_disablePolicy_cancelsNotifiedNotice() {
        assumeTrue(featureEnabled(), "reward_balance_expiration feature must be enabled for this tenant");

        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).plusDays(5);  // future, so warn fires but expire does not
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        assertThat(batchService.processWarnPhaseForPolicy(policy)).isEqualTo(1);
        assertThat(notice(wallet.getId(), fixedDate).getStatus()).isEqualTo(ExpiryNoticeStatus.NOTIFIED);

        // Disable via the policy service → triggers cancelPendingForPolicy
        UpsertBalanceExpirationPolicyRequest disable = request(false, ExpirationMode.FIXED_DATE, null, fixedDate, 30);
        policyService.upsertPolicy(CURRENCY, disable);

        BalanceExpiryNotice cancelled = notice(wallet.getId(), fixedDate);
        assertThat(cancelled.getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();

        // AC-6 audit (real DB): cancel-on-relax writes a synchronous CANCELLED / BALANCE_EXPIRATION_POLICY audit.
        // Only the cancel path writes a BALANCE_EXPIRATION_POLICY audit here (upsertPolicy is called directly,
        // so the controller @Audited aspect does not fire) and the client is fresh — non-zero confirms it.
        assertThat(auditLogRepository.countByClientIdAndResourceType(
                testClient.getId(), AuditResourceType.BALANCE_EXPIRATION_POLICY)).isGreaterThanOrEqualTo(1);
    }

    // ── Breakage report reflects expired amount (FR-09.6, US-04) ──────────────

    @Test
    void breakageReport_afterExpiry_reflectsExpiredAmount() {
        assumeTrue(featureEnabled(), "reward_balance_expiration feature must be enabled for this tenant");

        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));
        batchService.processWarnPhaseForPolicy(policy);
        batchService.processExpirePhaseForPolicy(policy);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BalanceBreakageReportResponse report = breakageReportService.getBreakage(
                today.minusDays(2), today.plusDays(1), CURRENCY, Granularity.MONTH);

        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows()).anySatisfy(row -> {
            assertThat(row.currencyId()).isEqualTo(CURRENCY);
            assertThat(row.totalExpiredAmount()).isEqualByComparingTo("100.00");
            assertThat(row.expiredCount()).isEqualTo(1L);
        });
    }

    // ── Config validation: lead ≥ inactivity → BusinessRuleException (FR-09.9) ─

    @Test
    void configValidation_leadGteInactivity_throwsBusinessRuleException() {
        assumeTrue(featureEnabled(), "reward_balance_expiration feature must be enabled for this tenant");

        UpsertBalanceExpirationPolicyRequest bad = request(true, ExpirationMode.INACTIVITY, 30, null, 30);
        assertThatThrownBy(() -> policyService.upsertPolicy(CURRENCY, bad))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactivity period");
    }

    // ── Kafka round-trip — warn phase publishes BALANCE_EXPIRING_SOON (US-02, FR-09.7) ─────────

    @Test
    void warnPhase_publishesBalanceExpiringSoonEvent_toNotificationTopic() throws Exception {
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = saveFixedDatePolicy(fixedDate, 30, daysAgo(60), true);
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));

        // Independent consumer on a fresh group: a true produce → broker → consume round-trip,
        // decoupled from the app's NotificationEventConsumer (which runs on its own group, so both
        // receive the message). The warn phase uses publishAndConfirm (blocks on broker ack), so the
        // event is on the broker the instant processWarnPhaseForPolicy returns — no produce-side
        // timing flakiness; the only wait is for our consumer to fetch it.
        try (KafkaConsumer<String, String> consumer = freshNotificationConsumer()) {
            consumer.subscribe(List.of(NOTIFICATION_TOPIC));
            awaitAssignment(consumer);
            consumer.seekToEnd(consumer.assignment());  // skip backlog — observe only THIS test's event
            // seekToEnd is lazy (resolves on the next fetching poll). Force it to resolve NOW via
            // position(), so the warn message produced below lands strictly AFTER our fetch position
            // instead of being skipped when the seek would otherwise resolve to a later end-offset.
            for (TopicPartition tp : consumer.assignment()) {
                consumer.position(tp);
            }

            assertThat(batchService.processWarnPhaseForPolicy(policy)).isEqualTo(1);

            NotificationEvent event = pollForClientEvent(consumer, testClient.getId(), Duration.ofSeconds(15));
            assertThat(event).as("a BALANCE_EXPIRING_SOON event for this client on " + NOTIFICATION_TOPIC).isNotNull();
            assertThat(event.notificationTypeKey()).isEqualTo("BALANCE_EXPIRING_SOON");
            assertThat(event.clientId()).isEqualTo(testClient.getId());
            assertThat(event.resourceType()).isEqualTo("BALANCE_EXPIRY_NOTICE");
            assertThat(event.metadata())
                    .containsEntry("walletId", wallet.getId().toString())
                    .containsEntry("currencyId", CURRENCY);
        }
    }

    // ── Inactivity expiry — new activity after warning cancels stale notice (Task 5 revalidation) ─

    @Test
    void inactivityExpiry_walletActiveAfterWarning_isNotExpired() throws Exception {
        // Arrange: INACTIVITY policy — 60-day inactivity window, 10-day lead, grace window passed.
        // enabledAt = 70 days ago so the full leadTimeDays (10) grace window has passed.
        BalanceExpirationPolicy policy = policyRepository.save(BalanceExpirationPolicy.builder()
                .clientId(testClient.getId())
                .currencyId(CURRENCY)
                .enabled(true)
                .expirationMode(ExpirationMode.INACTIVITY)
                .inactivityDays(60)
                .leadTimeDays(10)
                .enabledAt(daysAgo(70))
                .build());

        // Credit the wallet so a CREDIT ledger entry is created, then patch its created_at to 61 days ago.
        // This makes the wallet's last activity 61 days old → scheduledExpiryDate = today - 1 (yesterday,
        // so the expire phase will pick it up as due).
        RewardWallet wallet = creditWallet(new BigDecimal("100.00"));
        Instant oldActivity = daysAgo(61);
        // Patch all CREDIT entries for this wallet to simulate old activity.
        // @CreatedDate always overwrites the value on persist, so we correct via native SQL.
        jdbc.update(
                "UPDATE ledger_entries SET created_at = ? WHERE reward_wallet_id = ? AND entry_type = 'CREDIT'",
                java.sql.Timestamp.from(oldActivity),
                wallet.getId());

        // Warn phase: computes scheduledExpiryDate = oldActivity + 60d = today - 1 (yesterday), warns.
        int warned = batchService.processWarnPhaseForPolicy(policy);
        assertThat(warned).isEqualTo(1);

        LocalDate expectedExpiryDate = oldActivity.atZone(ZoneOffset.UTC).toLocalDate().plusDays(60);
        BalanceExpiryNotice noticeAfterWarn = notice(wallet.getId(), expectedExpiryDate);
        assertThat(noticeAfterWarn.getStatus()).isEqualTo(ExpiryNoticeStatus.NOTIFIED);

        // Act: record NEW activity (a fresh CREDIT) AFTER the warning — moves the inactivity clock forward.
        walletService.credit(testClient.getId(), testUser.getId(), CURRENCY,
                new BigDecimal("10.00"), "TEST_NEW_ACTIVITY", UUID.randomUUID(), "post-warn activity");

        // Act: run the expire phase.
        int expired = batchService.processExpirePhaseForPolicy(policy);

        // Assert: the stale-notice revalidation (Task 5) must cancel the notice without debiting.
        assertThat(expired).isEqualTo(0);
        assertThat(expiryEntries(wallet.getId())).isEmpty();

        RewardWallet afterExpiry = rewardWalletRepository.findById(wallet.getId()).orElseThrow();
        // Balance was 100 + 10 (new credit) = 110 — unchanged by the expire phase.
        assertThat(afterExpiry.getAvailableBalance()).isEqualByComparingTo("110.00");

        // Notice must be CANCELLED (stale-notice revalidation path in processExpireForNotice).
        BalanceExpiryNotice cancelledNotice = notice(wallet.getId(), expectedExpiryDate);
        assertThat(cancelledNotice.getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
        assertThat(cancelledNotice.getCancelledAt()).isNotNull();
    }

    // ── Company wallet expiry — BALANCE_EXPIRED targets only owning company's PARTNER_ADMINs (Task 1 resolver) ─

    @Test
    void companyWalletExpiry_notifiesOnlyOwningCompanyAdmins() throws Exception {
        // Arrange: two client roles — PARTNER_ADMIN and PARTNER_SELLER.
        ClientRole adminRole = clientRoleRepository.save(ClientRole.builder()
                .clientId(testClient.getId())
                .name("Partner Admin " + UUID.randomUUID())
                .baseRoleName("PARTNER_ADMIN")
                .system(true)
                .roleType("EXTERNAL")
                .build());
        ClientRole sellerRole = clientRoleRepository.save(ClientRole.builder()
                .clientId(testClient.getId())
                .name("Partner Seller " + UUID.randomUUID())
                .baseRoleName("PARTNER_SELLER")
                .system(true)
                .roleType("EXTERNAL")
                .build());

        // Two partner companies under the same tenant. external_partner_id is NOT NULL with a unique
        // (client_id, external_partner_id) index — give each a distinct value.
        PartnerCompany companyA = partnerCompanyRepository.save(PartnerCompany.builder()
                .clientId(testClient.getId())
                .name("Company A " + UUID.randomUUID())
                .externalPartnerId("ext-a-" + UUID.randomUUID())
                .build());
        PartnerCompany companyB = partnerCompanyRepository.save(PartnerCompany.builder()
                .clientId(testClient.getId())
                .name("Company B " + UUID.randomUUID())
                .externalPartnerId("ext-b-" + UUID.randomUUID())
                .build());

        // Company A: one PARTNER_ADMIN + one PARTNER_SELLER.
        User adminA = userRepository.save(User.builder()
                .email("admin-a-" + UUID.randomUUID() + "@test.com")
                .firstName("AdminA").lastName("Test")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(companyA.getId())
                .clientRoleId(adminRole.getId())
                .build());
        User sellerA = userRepository.save(User.builder()
                .email("seller-a-" + UUID.randomUUID() + "@test.com")
                .firstName("SellerA").lastName("Test")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(companyA.getId())
                .clientRoleId(sellerRole.getId())
                .build());

        // Company B: one PARTNER_ADMIN — must NOT appear in the notification.
        User adminB = userRepository.save(User.builder()
                .email("admin-b-" + UUID.randomUUID() + "@test.com")
                .firstName("AdminB").lastName("Test")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(companyB.getId())
                .clientRoleId(adminRole.getId())
                .build());

        // COMPANY wallet owned by company A.
        RewardWallet companyWallet = walletService.creditCompany(
                testClient.getId(), companyA.getId(), CURRENCY,
                new BigDecimal("200.00"), "TEST", null, "seed");

        // Create a FIXED_DATE policy with a due date (yesterday) — we use FIXED_DATE here so the
        // expire phase picks up the pre-seeded notice without needing to run the warn phase.
        LocalDate dueDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpirationPolicy policy = policyRepository.save(BalanceExpirationPolicy.builder()
                .clientId(testClient.getId())
                .currencyId(CURRENCY)
                .enabled(true)
                .expirationMode(ExpirationMode.FIXED_DATE)
                .fixedExpiryDate(dueDate)
                .leadTimeDays(30)
                .enabledAt(daysAgo(60))
                .build());

        // Seed a NOTIFIED notice directly — simulates the warn phase having already fired.
        noticeRepository.save(BalanceExpiryNotice.builder()
                .clientId(testClient.getId())
                .walletId(companyWallet.getId())
                .currencyId(CURRENCY)
                .policyId(policy.getId())
                .scheduledExpiryDate(dueDate)
                .status(ExpiryNoticeStatus.NOTIFIED)
                .notifiedAt(Instant.now().minusSeconds(3600))
                .notifiedAmount(new BigDecimal("200.00"))
                .build());

        // Act: subscribe to the notification topic BEFORE triggering the expire phase, so we don't
        // miss the BALANCE_EXPIRED event. Mirror the Kafka pattern from the warnPhase round-trip test.
        try (KafkaConsumer<String, String> consumer = freshNotificationConsumer()) {
            consumer.subscribe(List.of(NOTIFICATION_TOPIC));
            awaitAssignment(consumer);
            consumer.seekToEnd(consumer.assignment());
            for (TopicPartition tp : consumer.assignment()) {
                consumer.position(tp);
            }

            int expired = batchService.processExpirePhaseForPolicy(policy);
            assertThat(expired).isEqualTo(1);

            // Assert: a BALANCE_EXPIRED event was published for this client.
            NotificationEvent event = pollForClientEvent(consumer, testClient.getId(), Duration.ofSeconds(15));
            assertThat(event).as("a BALANCE_EXPIRED event for this client on " + NOTIFICATION_TOPIC).isNotNull();
            assertThat(event.notificationTypeKey()).isEqualTo("BALANCE_EXPIRED");
            assertThat(event.clientId()).isEqualTo(testClient.getId());

            // The event's targetUserIds must contain ONLY company A's PARTNER_ADMIN.
            assertThat(event.targetUserIds())
                    .as("only company A's PARTNER_ADMIN should be targeted")
                    .containsExactly(adminA.getId());

            // Must NOT contain company A's PARTNER_SELLER.
            assertThat(event.targetUserIds())
                    .as("company A's PARTNER_SELLER must NOT be targeted")
                    .doesNotContain(sellerA.getId());

            // Must NOT contain company B's PARTNER_ADMIN.
            assertThat(event.targetUserIds())
                    .as("company B's PARTNER_ADMIN must NOT be targeted")
                    .doesNotContain(adminB.getId());
        }

        // Cleanup extra rows created by this test (tearDown only deletes by testClient FK for standard rows).
        safeDelete(() -> userRepository.delete(adminA));
        safeDelete(() -> userRepository.delete(sellerA));
        safeDelete(() -> userRepository.delete(adminB));
        safeDelete(() -> partnerCompanyRepository.delete(companyA));
        safeDelete(() -> partnerCompanyRepository.delete(companyB));
        safeDelete(() -> clientRoleRepository.delete(adminRole));
        safeDelete(() -> clientRoleRepository.delete(sellerRole));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean featureEnabled() {
        return featureFlagService.getEnabledFeatures(testClient.getId()).contains("reward_balance_expiration");
    }

    private BalanceExpirationPolicy saveFixedDatePolicy(LocalDate fixedDate, int leadDays, Instant enabledAt, boolean enabled) {
        return policyRepository.save(BalanceExpirationPolicy.builder()
                .clientId(testClient.getId())
                .currencyId(CURRENCY)
                .enabled(enabled)
                .expirationMode(ExpirationMode.FIXED_DATE)
                .fixedExpiryDate(fixedDate)
                .leadTimeDays(leadDays)
                .enabledAt(enabledAt)
                .build());
    }

    private RewardWallet creditWallet(BigDecimal amount) {
        return walletService.credit(testClient.getId(), testUser.getId(), CURRENCY, amount, "TEST", null, "seed");
    }

    private BalanceExpiryNotice notice(UUID walletId, LocalDate scheduledExpiryDate) {
        Optional<BalanceExpiryNotice> n = noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                testClient.getId(), walletId, CURRENCY, scheduledExpiryDate);
        return n.orElseThrow(() -> new AssertionError("expected a notice for wallet " + walletId));
    }

    private List<LedgerEntry> expiryEntries(UUID walletId) {
        return ledgerEntryRepository.findByRewardWalletId(walletId, PageRequest.of(0, 50)).getContent().stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.EXPIRY)
                .toList();
    }

    private UpsertBalanceExpirationPolicyRequest request(boolean enabled, ExpirationMode mode,
                                                         Integer inactivityDays, LocalDate fixedDate, int leadDays) {
        UpsertBalanceExpirationPolicyRequest r = new UpsertBalanceExpirationPolicyRequest();
        r.setEnabled(enabled);
        r.setExpirationMode(mode);
        r.setInactivityDays(inactivityDays);
        r.setFixedExpiryDate(fixedDate);
        r.setLeadTimeDays(leadDays);
        return r;
    }

    private Instant daysAgo(int days) {
        return Instant.now().minusSeconds((long) days * 86400L);
    }

    private KafkaConsumer<String, String> freshNotificationConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-balexp-roundtrip-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new KafkaConsumer<>(props);
    }

    private void awaitAssignment(KafkaConsumer<String, String> consumer) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200));  // drives the group join + partition assignment
        }
        assertThat(consumer.assignment()).as("consumer joined group + was assigned partitions").isNotEmpty();
    }

    private NotificationEvent pollForClientEvent(KafkaConsumer<String, String> consumer, UUID clientId, Duration timeout)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(clientId.toString())) {
                    return objectMapper.readValue(record.value(), NotificationEvent.class);
                }
            }
        }
        return null;
    }

    private void setSecurityContext(User user, String baseRole) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_" + baseRole)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void safeDelete(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
