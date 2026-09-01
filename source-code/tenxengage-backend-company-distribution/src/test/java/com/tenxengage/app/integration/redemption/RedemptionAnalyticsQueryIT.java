package com.tenxengage.app.integration.redemption;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.response.redemption.CurrencyTypeRateDto;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.service.redemption.RedemptionAnalyticsService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import com.tenxengage.app.testdata.RedemptionRequestFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — Aggregation accuracy for analytics queries.
 *
 * Inserts known LedgerEntry rows and verifies that the service produces the
 * correct ratePercentage. Cache is bypassed by using a unique clientId per test
 * (guaranteed cache miss on every run).
 *
 * Covers: T-12 from test-plan.md.
 */
@Tag("integration")
class RedemptionAnalyticsQueryIT extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAnalyticsService analyticsService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;

    private Client testClient;
    private User testUser;
    private RewardWallet testWallet;

    private final List<UUID> catalogItemsToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
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
        // Requests reference wallets, catalog items, users — delete first
        redemptionRequestRepository.deleteAll(
                redemptionRequestRepository.findByClientIdAndDeletedFalse(
                        testClient.getId(), PageRequest.of(0, 1000)).getContent());
        ledgerEntryRepository.deleteAll(
                ledgerEntryRepository.findByClientId(testClient.getId(), PageRequest.of(0, 10000)).getContent());
        // Catalog items are global — delete after requests that reference them
        catalogItemsToCleanup.forEach(id -> catalogItemRepository.deleteById(id));
        // Delete ALL wallets for this user — tests may add wallets beyond testWallet
        rewardWalletRepository.deleteAll(
                rewardWalletRepository.findByClientIdAndUserId(testClient.getId(), testUser.getId()));
        userRepository.delete(testUser);
        clientRepository.delete(testClient);
        SecurityContextHolder.clearContext();
    }

    /**
     * T-12: Insert 4 CREDIT (100+200+100+50=450) and 2 DEBIT (100+100=200) for currencyId="cash".
     * Expected ratePercentage = 200/450 × 100 = 44.44 (HALF_UP, 2 dp).
     */
    @Test
    void redemptionRate_correctPercentage_knownLedgerData() {
        // 4 CREDIT entries (denominator)
        saveLedgerEntry(LedgerEntryType.CREDIT, "100.00");
        saveLedgerEntry(LedgerEntryType.CREDIT, "200.00");
        saveLedgerEntry(LedgerEntryType.CREDIT, "100.00");
        saveLedgerEntry(LedgerEntryType.CREDIT, "50.00");
        // 2 DEBIT entries (numerator)
        saveLedgerEntry(LedgerEntryType.DEBIT, "100.00");
        saveLedgerEntry(LedgerEntryType.DEBIT, "100.00");

        LocalDate today = LocalDate.now();
        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        CurrencyTypeRateDto cashRate = response.redemptionRates().stream()
                .filter(r -> "cash".equals(r.currencyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rate card for 'cash'"));

        assertThat(cashRate.hasActivity()).isTrue();
        assertThat(cashRate.numerator()).isEqualTo(200L);
        assertThat(cashRate.denominator()).isEqualTo(450L);
        // 200/450 × 100 = 44.4444... → HALF_UP 2dp → "44.44"
        assertThat(cashRate.ratePercentage()).isEqualTo("44.44");
    }

    @Test
    void redemptionRate_zeroRedeemed_showsZeroPercentage() {
        // Earned but nothing redeemed yet: denominator>0 so hasActivity=true, rate="0.00"
        saveLedgerEntry(LedgerEntryType.CREDIT, "500.00");

        LocalDate today = LocalDate.now();
        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        CurrencyTypeRateDto cashRate = response.redemptionRates().stream()
                .filter(r -> "cash".equals(r.currencyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rate card for 'cash'"));

        assertThat(cashRate.hasActivity()).isTrue();
        assertThat(cashRate.denominator()).isEqualTo(500L);
        assertThat(cashRate.numerator()).isEqualTo(0L);
        assertThat(cashRate.ratePercentage()).isEqualTo("0.00");
    }

    @Test
    void unredeemedBalance_snapshotReflectsWalletBalances() {
        rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(testClient.getId(), testUser.getId())
                        .currencyId("points")
                        .availableBalance(new BigDecimal("750.00"))
                        .reservedBalance(new BigDecimal("250.00"))
                        .build());
        // Need at least one ledger entry so currencyIds includes "points"
        LedgerEntry le = LedgerEntry.builder()
                .clientId(testClient.getId())
                .rewardWalletId(testWallet.getId())
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("1.00"))
                .currencyId("points")
                .availableBalanceBefore(BigDecimal.ZERO)
                .availableBalanceAfter(new BigDecimal("1.00"))
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO)
                .build();
        ledgerEntryRepository.save(le);

        LocalDate today = LocalDate.now();
        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        var pointsBalance = response.unredeemedBalances().stream()
                .filter(b -> "points".equals(b.currencyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No balance card for 'points'"));

        assertThat(pointsBalance.availableBalance()).isEqualTo(750L);
        assertThat(pointsBalance.reservedBalance()).isEqualTo(250L);
        assertThat(pointsBalance.totalOutstanding()).isEqualTo(1000L);
    }

    /**
     * T-12: Failed/cancelled rate uses windowed request counts.
     * 2 FAILED + 1 CANCELLED = 3 out of 5 in-window requests → 60.00%.
     * Out-of-window FAILED is excluded from both denominator and numerator.
     */
    @Test
    void failedCancelledRate_correctPercentage_windowedOnly() {
        LocalDate today = LocalDate.now();
        LocalDate queryFrom = today.minusDays(30);
        Instant inWindow = today.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant outOfWindow = today.minusDays(60).atStartOfDay(ZoneOffset.UTC).toInstant();

        // currencyIds are driven by wallets; testWallet.currencyId="cash" is already sufficient.
        // No ledger entries needed for "cash" to appear in failedCancelledRates.
        RedemptionCatalogItem catalogItem = createCatalogItem();
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED,    "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED,    "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.CANCELLED, "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.COMPLETED, "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.COMPLETED, "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED,    "cash", outOfWindow); // excluded

        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(queryFrom, today);

        CurrencyTypeRateDto cashRate = response.failedCancelledRates().stream()
                .filter(r -> "cash".equals(r.currencyId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No failed/cancelled rate for 'cash'"));

        assertThat(cashRate.denominator()).as("in-window total").isEqualTo(5L);
        assertThat(cashRate.numerator()).as("in-window failed+cancelled").isEqualTo(3L);
        assertThat(cashRate.ratePercentage()).isEqualTo("60.00");
        assertThat(cashRate.hasActivity()).isTrue();
    }

    /**
     * T-12: totalRedemptionCount merges PENDING_APPROVAL and RESERVED into the "PENDING" key.
     * Seeds 8 requests across all statuses, verifies both total and per-key breakdown.
     */
    @Test
    void totalRedemptionCount_byStatus_mergesPendingApprovalAndReserved() {
        LocalDate today = LocalDate.now();
        Instant inWindow = today.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant();

        RedemptionCatalogItem catalogItem = createCatalogItem();
        saveRequest(catalogItem.getId(), RedemptionStatus.PENDING_APPROVAL, "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.PENDING_APPROVAL, "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.RESERVED,         "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.PROCESSING,       "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.COMPLETED,        "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.COMPLETED,        "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED,           "cash", inWindow);
        saveRequest(catalogItem.getId(), RedemptionStatus.CANCELLED,        "cash", inWindow);

        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        var count = response.totalRedemptionCount();
        assertThat(count.total()).isEqualTo(8L);
        assertThat(count.byStatus().get("PENDING")).as("PENDING_APPROVAL(2) + RESERVED(1)").isEqualTo(3L);
        assertThat(count.byStatus().get("PROCESSING")).isEqualTo(1L);
        assertThat(count.byStatus().get("COMPLETED")).isEqualTo(2L);
        assertThat(count.byStatus().get("FAILED")).isEqualTo(1L);
        assertThat(count.byStatus().get("CANCELLED")).isEqualTo(1L);
        assertThat(count.hasActivity()).isTrue();
    }

    /**
     * T-12: No ledger entries for tenant — currency IDs are driven by wallets, not ledger entries.
     * testWallet.currencyId="cash" → response contains one "cash" card with zero rates/balances
     * and totalRedemptionCount=0 since no requests exist.
     */
    @Test
    void noLedgerEntries_ratesAreZero_currencyDerivedFromWallet() {
        LocalDate today = LocalDate.now();

        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        // currencyIds from wallets: testWallet has "cash" → one card per list
        assertThat(response.redemptionRates()).hasSize(1);
        CurrencyTypeRateDto cashRate = response.redemptionRates().get(0);
        assertThat(cashRate.currencyId()).isEqualTo("cash");
        assertThat(cashRate.denominator()).isEqualTo(0L);
        assertThat(cashRate.numerator()).isEqualTo(0L);
        assertThat(cashRate.hasActivity()).isFalse();

        // No requests → counts are all zero
        assertThat(response.totalRedemptionCount().total()).isEqualTo(0L);
        assertThat(response.totalRedemptionCount().hasActivity()).isFalse();
    }

    /**
     * T-12: Lifetime metrics (redemptionRates) are unaffected by the date window;
     * windowed metrics (failedCancelledRates, totalRedemptionCount) exclude out-of-window requests.
     */
    @Test
    void lifetimeMetrics_unaffectedByDateWindow_windowedMetricsFiltered() {
        LocalDate today = LocalDate.now();
        LocalDate queryFrom = today.minusDays(30);
        Instant inWindow   = today.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant outOfWindow = today.minusDays(60).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Lifetime entries: 200 earned, 100 redeemed → 50.00%
        saveLedgerEntry(LedgerEntryType.CREDIT, "200.00");
        saveLedgerEntry(LedgerEntryType.DEBIT,  "100.00");

        RedemptionCatalogItem catalogItem = createCatalogItem();
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED, "cash", inWindow);    // in window
        saveRequest(catalogItem.getId(), RedemptionStatus.FAILED, "cash", outOfWindow); // excluded

        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(queryFrom, today);

        // Lifetime: 200 CREDIT, 100 DEBIT — unchanged by any date filter
        CurrencyTypeRateDto lifetime = response.redemptionRates().stream()
                .filter(r -> "cash".equals(r.currencyId()))
                .findFirst()
                .orElseThrow();
        assertThat(lifetime.denominator()).isEqualTo(200L);
        assertThat(lifetime.numerator()).isEqualTo(100L);
        assertThat(lifetime.ratePercentage()).isEqualTo("50.00");

        // Windowed: 1 total in window, 1 failed → 100.00%
        CurrencyTypeRateDto windowed = response.failedCancelledRates().stream()
                .filter(r -> "cash".equals(r.currencyId()))
                .findFirst()
                .orElseThrow();
        assertThat(windowed.denominator()).as("only in-window request counted").isEqualTo(1L);
        assertThat(windowed.numerator()).isEqualTo(1L);

        // Total count: only the in-window request
        assertThat(response.totalRedemptionCount().total()).isEqualTo(1L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private RedemptionCatalogItem createCatalogItem() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem().build());
        catalogItemsToCleanup.add(item.getId());
        return item;
    }

    private void saveRequest(UUID catalogItemId, RedemptionStatus status, String currencyId, Instant submittedAt) {
        redemptionRequestRepository.save(
                RedemptionRequestFixtures.withStatus(
                        testClient.getId(), testUser.getId(), testWallet.getId(), catalogItemId, status)
                        .currencyId(currencyId)
                        .submittedAt(submittedAt)
                        .build());
    }

    private void saveLedgerEntry(LedgerEntryType type, String amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .clientId(testClient.getId())
                .rewardWalletId(testWallet.getId())
                .entryType(type)
                .amount(new BigDecimal(amount))
                .currencyId("cash")
                .availableBalanceBefore(BigDecimal.ZERO)
                .availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO)
                .build();
        ledgerEntryRepository.save(entry);
    }

    private void setSecurityContext(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
