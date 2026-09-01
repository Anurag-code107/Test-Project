package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.WalletService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for wallet lifecycle, business rules, concurrency, and tenant isolation.
 *
 * No class-level @Transactional: WalletService uses REQUIRES_NEW internally, so setup data
 * must be committed before service calls fire. Each test cleans up its own data in @AfterEach.
 */
@Tag("integration")
class WalletIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private WalletService walletService;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private DataSource dataSource;

    private Client testClient;
    private Client otherClient;
    private User testUser;
    private User otherUser;
    private PartnerCompany testCompany;
    private PartnerCompany otherCompany;

    @BeforeEach
    void setUp() {
        // No surrounding transaction — all saves auto-commit, visible to REQUIRES_NEW inner txns
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        otherClient = clientRepository.save(ClientFixtures.activeEnterprise().build());

        testCompany = partnerCompanyRepository.save(PartnerFixtures.activeReseller(testClient.getId()).build());
        otherCompany = partnerCompanyRepository.save(PartnerFixtures.activeReseller(testClient.getId()).build());

        testUser = userRepository.save(User.builder()
                .email("wallet-test-" + UUID.randomUUID() + "@test.com")
                .firstName("Test").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testCompany.getId())
                .build());

        otherUser = userRepository.save(User.builder()
                .email("wallet-other-" + UUID.randomUUID() + "@test.com")
                .firstName("Other").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(otherClient.getId())
                .build());

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "PARTNER_SELLER");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        // Delete in FK order: ledger_entries → reward_wallets → users → companies → clients
        deleteLedgerAndWalletsForClient(testClient.getId());
        deleteLedgerAndWalletsForClient(otherClient.getId());
        safeDelete(() -> userRepository.delete(testUser));
        safeDelete(() -> userRepository.delete(otherUser));
        safeDelete(() -> partnerCompanyRepository.delete(testCompany));
        safeDelete(() -> partnerCompanyRepository.delete(otherCompany));
        safeDelete(() -> clientRepository.delete(testClient));
        safeDelete(() -> clientRepository.delete(otherClient));
    }

    // =========================================================================
    // Schema (Lifecycle & CRUD)
    // =========================================================================

    @Test
    void v6Migration_rewardWalletsTableHasRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var cols = conn.getMetaData().getColumns(null, null, "reward_wallets", null)) {
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains(
                    "id", "client_id", "user_id", "partner_company_id",
                    "currency_id", "wallet_type",
                    "available_balance", "reserved_balance", "version");
        }
    }

    @Test
    void v7Migration_ledgerEntriesTableHasRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var cols = conn.getMetaData().getColumns(null, null, "ledger_entries", null)) {
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains(
                    "id", "client_id", "reward_wallet_id", "entry_type",
                    "amount", "currency_id", "reference_type", "reference_id", "note",
                    "available_balance_before", "available_balance_after",
                    "reserved_balance_before", "reserved_balance_after");
        }
    }

    @Test
    void credit_autoCreatesWallet_andWritesLedgerEntry() {
        BigDecimal amount = new BigDecimal("50.00");
        RewardWallet wallet = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                amount, "TEST", null, "auto-create");

        assertThat(wallet.getId()).isNotNull();
        assertThat(wallet.getWalletType()).isEqualTo(WalletType.INDIVIDUAL);
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(amount);
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        var entries = ledgerEntryRepository
                .findByRewardWalletId(wallet.getId(), PageRequest.of(0, 10));
        assertThat(entries.getTotalElements()).isEqualTo(1);
        LedgerEntry entry = entries.getContent().get(0);
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(entry.getAmount()).isEqualByComparingTo(amount);
        assertThat(entry.getAvailableBalanceBefore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getAvailableBalanceAfter()).isEqualByComparingTo(amount);
    }

    // =========================================================================
    // Business Rule Enforcement
    // =========================================================================

    @Test
    void reserve_throwsBusinessRuleException_whenInsufficientBalance() {
        // Use the service to create a committed wallet at 5.00
        RewardWallet wallet = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("5.00"), "SETUP", null, null);

        assertThatThrownBy(() ->
                walletService.reserve(wallet.getId(), new BigDecimal("10.00"), "TEST", UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);

        RewardWallet unchanged = rewardWalletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(unchanged.getAvailableBalance()).isEqualByComparingTo("5.00");

        long ledgerCount = ledgerEntryRepository
                .findByRewardWalletId(wallet.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(ledgerCount).isEqualTo(1); // only the initial CREDIT entry
    }

    @Test
    void credit_ledgerSnapshotIntegrity() {
        // First credit establishes the wallet at 50.00
        walletService.credit(testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("50.00"), "SETUP", null, null);

        // Second credit should record before=50, after=150
        RewardWallet wallet = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "TEST", null, null);

        var entries = ledgerEntryRepository
                .findByRewardWalletId(wallet.getId(), PageRequest.of(0, 10));
        assertThat(entries.getTotalElements()).isEqualTo(2);

        LedgerEntry second = entries.getContent().stream()
                .filter(e -> e.getAvailableBalanceBefore().compareTo(new BigDecimal("50.00")) == 0)
                .findFirst().orElseThrow();
        assertThat(second.getAvailableBalanceBefore()).isEqualByComparingTo("50.00");
        assertThat(second.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(second.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void credit_idempotency_sameReferenceIdCreditedOnce() {
        UUID refId = UUID.randomUUID();

        RewardWallet first = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "INCENTIVE", refId, null);

        RewardWallet second = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "INCENTIVE", refId, null);

        assertThat(second.getAvailableBalance()).isEqualByComparingTo(first.getAvailableBalance());

        long ledgerCount = ledgerEntryRepository
                .findByRewardWalletId(first.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(ledgerCount).isEqualTo(1);
    }

    @Test
    void creditReserveDebit_fullLifecycle() {
        RewardWallet afterCredit = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "TEST", null, null);
        assertThat(afterCredit.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(afterCredit.getReservedBalance()).isEqualByComparingTo("0.00");

        RewardWallet afterReserve = walletService.reserve(
                afterCredit.getId(), new BigDecimal("30.00"), "REDEMPTION", UUID.randomUUID());
        assertThat(afterReserve.getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(afterReserve.getReservedBalance()).isEqualByComparingTo("30.00");

        RewardWallet afterDebit = walletService.debit(
                afterCredit.getId(), new BigDecimal("30.00"), "REDEMPTION", UUID.randomUUID());
        assertThat(afterDebit.getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(afterDebit.getReservedBalance()).isEqualByComparingTo("0.00");

        long ledgerCount = ledgerEntryRepository
                .findByRewardWalletId(afterCredit.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(ledgerCount).isEqualTo(3);
    }

    @Test
    void creditReserveRelease_fullLifecycle() {
        RewardWallet afterCredit = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "TEST", null, null);

        RewardWallet afterReserve = walletService.reserve(
                afterCredit.getId(), new BigDecimal("30.00"), "REDEMPTION", UUID.randomUUID());
        assertThat(afterReserve.getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(afterReserve.getReservedBalance()).isEqualByComparingTo("30.00");

        RewardWallet afterRelease = walletService.release(
                afterCredit.getId(), new BigDecimal("30.00"), "REDEMPTION_CANCEL", UUID.randomUUID());
        assertThat(afterRelease.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(afterRelease.getReservedBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void rewardGrantService_creditPath_writesLedgerEntryWithIncentiveReferenceType() {
        UUID transactionId = UUID.randomUUID();
        RewardWallet wallet = walletService.credit(
                testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("75.00"), "INCENTIVE", transactionId, null);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("75.00");

        var entries = ledgerEntryRepository
                .findByRewardWalletId(wallet.getId(), PageRequest.of(0, 10));
        assertThat(entries.getContent().get(0).getReferenceType()).isEqualTo("INCENTIVE");
        assertThat(entries.getContent().get(0).getReferenceId()).isEqualTo(transactionId);
    }

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Test
    void concurrentCredit_createsSingleWallet_andSumsBothAmounts() throws Exception {
        // Dedicated client + user for this test — committed by setUp's sibling pattern
        Client cClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        User cUser = userRepository.save(User.builder()
                .email("concurrent-" + UUID.randomUUID() + "@test.com")
                .firstName("C").lastName("U")
                .passwordHash("$2a$10$x")
                .status(UserStatus.ACTIVE)
                .clientId(cClient.getId())
                .build());

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    TenantContext.setClientId(cClient.getId());
                    ready.countDown();
                    go.await();
                    walletService.credit(cClient.getId(), cUser.getId(), "cash",
                            new BigDecimal("10.00"), "TEST", UUID.randomUUID(), null);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(errors.get()).isZero();

        List<RewardWallet> wallets = rewardWalletRepository
                .findByClientIdAndUserIdAndWalletType(cClient.getId(), cUser.getId(), WalletType.INDIVIDUAL);
        assertThat(wallets).hasSize(1);
        assertThat(wallets.get(0).getAvailableBalance()).isEqualByComparingTo("20.00");

        long ledgerCount = ledgerEntryRepository
                .findByRewardWalletId(wallets.get(0).getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(ledgerCount).isEqualTo(2);

        // Cleanup concurrency-specific data
        deleteLedgerAndWalletsForWallet(wallets.get(0).getId());
        safeDelete(() -> userRepository.delete(cUser));
        safeDelete(() -> clientRepository.delete(cClient));
    }

    // =========================================================================
    // Tenant Isolation & Security
    // =========================================================================

    @Test
    void getUserWallets_crossTenantUserId_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> walletService.getUserWallets(otherUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompanyWallets_partnerAdminCompanyMismatch_throwsAccessDeniedException() {
        setSecurityContext(testUser, "PARTNER_ADMIN");
        assertThatThrownBy(() -> walletService.getCompanyWallets(otherCompany.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMyWallets_tenantIsolation_eachUserSeesOnlyOwnWallets() {
        walletService.credit(testClient.getId(), testUser.getId(), "cash",
                new BigDecimal("100.00"), "TEST", null, null);

        // testUser sees their wallet
        setSecurityContext(testUser, "PARTNER_SELLER");
        TenantContext.setClientId(testClient.getId());
        var myWallets = walletService.getMyWallets();
        assertThat(myWallets).isNotEmpty();

        // otherUser (different tenant) sees nothing in their own tenant context
        setSecurityContext(otherUser, "PARTNER_SELLER");
        TenantContext.setClientId(otherClient.getId());
        var otherWallets = walletService.getMyWallets();
        assertThat(otherWallets).isEmpty();

        // Restore for @AfterEach
        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "PARTNER_SELLER");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setSecurityContext(User user, String baseRole) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_" + baseRole)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void deleteLedgerAndWalletsForClient(UUID clientId) {
        var ledgerPage = ledgerEntryRepository.findByClientId(clientId, PageRequest.of(0, 10000));
        ledgerEntryRepository.deleteAll(ledgerPage.getContent());

        for (WalletType type : WalletType.values()) {
            List<RewardWallet> wallets;
            if (type == WalletType.INDIVIDUAL) {
                wallets = rewardWalletRepository.findByClientIdAndUserId(clientId, testUser.getId());
                rewardWalletRepository.deleteAll(wallets);
                wallets = rewardWalletRepository.findByClientIdAndUserId(clientId, otherUser.getId());
                rewardWalletRepository.deleteAll(wallets);
            } else {
                var companyWallets = rewardWalletRepository
                        .findByClientIdAndPartnerCompanyIdAndWalletType(clientId, testCompany.getId(), type);
                rewardWalletRepository.deleteAll(companyWallets);
                companyWallets = rewardWalletRepository
                        .findByClientIdAndPartnerCompanyIdAndWalletType(clientId, otherCompany.getId(), type);
                rewardWalletRepository.deleteAll(companyWallets);
            }
        }
    }

    private void deleteLedgerAndWalletsForWallet(UUID walletId) {
        var entries = ledgerEntryRepository.findByRewardWalletId(walletId, PageRequest.of(0, 10000));
        ledgerEntryRepository.deleteAll(entries.getContent());
        rewardWalletRepository.deleteById(walletId);
    }

    private void safeDelete(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
