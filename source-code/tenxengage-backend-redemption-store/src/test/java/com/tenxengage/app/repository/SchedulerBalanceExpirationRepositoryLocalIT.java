package com.tenxengage.app.repository;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.repository.projection.WalletLastActivityProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DB-backed validation (local Postgres via the {@code localtest} profile) for the two
 * performance-fix queries on {@link SchedulerBalanceExpirationRepository}:
 *   - AC-10: paged candidate-wallet query (bounds batch memory for large tenants).
 *   - AC-9:  bulk last-activity GROUP BY (replaces the per-wallet N+1).
 *
 * <p>The mocked unit tests in {@code BalanceExpiryBatchServiceTest} verify the service logic; this
 * exercises the actual JPQL, the {@code IN} filter, the {@code GROUP BY}, the interface-projection
 * mapping, and {@code Pageable} translation against a real database. (Context startup itself already
 * proves both {@code @Query} statements parse + map to the entity model — a malformed query fails
 * the Spring context boot.)
 *
 * <p>Reuses existing wallets rather than seeding new ones — {@code reward_wallets} carries FKs to
 * {@code clients}/{@code users} plus a {@code chk_wallet_owner} check, and {@code ledger_entries}
 * FKs {@code reward_wallet_id}, so fabricating valid rows is impractical in isolation. A unique
 * per-run {@code currencyId} keeps the assertions independent of the wallets' real ledger data.
 * {@code @Transactional} rolls back the seeded ledger rows.
 */
@Tag("integration")
@Transactional
class SchedulerBalanceExpirationRepositoryLocalIT extends AbstractLocalIntegrationTest {

    private static final Set<LedgerEntryType> ACTIVITY_TYPES = EnumSet.of(
            LedgerEntryType.CREDIT, LedgerEntryType.DEBIT,
            LedgerEntryType.RESERVE, LedgerEntryType.RETURN_CREDIT);

    @Autowired private SchedulerBalanceExpirationRepository schedulerRepo;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private EntityManager entityManager;

    // ── AC-10: paged candidate query executes + bounds the result ─────────────

    @Test
    void findExpiryCandidateWallets_executesAndRespectsPageLimit() {
        List<RewardWallet> wallets = rewardWalletRepository.findAll();
        assumeTrue(!wallets.isEmpty(), "needs at least one existing reward wallet");
        RewardWallet sample = wallets.get(0);

        // Page limit is honoured (≤ requested size) for a real (client, currency) with data.
        List<RewardWallet> page = schedulerRepo.findExpiryCandidateWallets(
                sample.getClientId(), sample.getCurrencyId(), PageRequest.of(0, 1));
        assertThat(page).hasSizeLessThanOrEqualTo(1);

        // A currency no wallet uses → empty (query filters correctly, terminates the sweep loop).
        assertThat(schedulerRepo.findExpiryCandidateWallets(
                sample.getClientId(), "itc-" + UUID.randomUUID(), PageRequest.of(0, 50))).isEmpty();
    }

    // ── AC-9: bulk last-activity GROUP BY + entry-type filter ─────────────────

    @Test
    void findLastActivityForWallets_groupsByWallet_andFiltersToActivityTypes() {
        // Find a client that owns ≥2 wallets so we can test inclusion + exclusion under one clientId.
        Map<UUID, List<RewardWallet>> byClient = rewardWalletRepository.findAll().stream()
                .collect(Collectors.groupingBy(RewardWallet::getClientId));
        var multi = byClient.values().stream().filter(ws -> ws.size() >= 2).findFirst();
        assumeTrue(multi.isPresent(), "needs a client with at least two reward wallets");

        UUID clientId = multi.get().get(0).getClientId();
        UUID withActivity = multi.get().get(0).getId();
        UUID onlyExpiry = multi.get().get(1).getId();
        String currency = "itc-" + UUID.randomUUID();  // unique → isolates from real ledger data

        // withActivity: a CREDIT (activity) + a later EXPIRY (non-activity) → returned, keyed by wallet.
        saveLedger(clientId, withActivity, currency, LedgerEntryType.CREDIT, new BigDecimal("100.00"));
        saveLedger(clientId, withActivity, currency, LedgerEntryType.EXPIRY, new BigDecimal("100.00"));
        // onlyExpiry: only a non-activity EXPIRY entry → must NOT appear in the activity-filtered result.
        saveLedger(clientId, onlyExpiry, currency, LedgerEntryType.EXPIRY, new BigDecimal("50.00"));

        List<WalletLastActivityProjection> rows = schedulerRepo.findLastActivityForWallets(
                clientId, currency, List.of(withActivity, onlyExpiry), ACTIVITY_TYPES);

        // Exactly one row — only the wallet with an activity-type entry; projection maps both fields.
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getWalletId()).isEqualTo(withActivity);
        assertThat(rows.get(0).getLastActivityAt()).isNotNull();
    }

    // ── AC-9 (single-wallet): findLastActivityAt ──────────────────────────────

    @Test
    void findLastActivityAt_returnsMaxCreatedAt_overActivityTypesOnly() {
        // Reuse an existing wallet so FK constraints are satisfied.
        List<RewardWallet> wallets = rewardWalletRepository.findAll();
        assumeTrue(!wallets.isEmpty(), "needs at least one existing reward wallet");
        RewardWallet w = wallets.get(0);
        UUID clientId = w.getClientId();
        UUID walletId = w.getId();
        String currency = "itc-" + UUID.randomUUID();

        Instant tMinus10 = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant tMinus3  = Instant.now().minus(3,  ChronoUnit.DAYS);
        Instant tMinus1  = Instant.now().minus(1,  ChronoUnit.DAYS);

        saveLedgerAt(clientId, walletId, currency, LedgerEntryType.CREDIT, new BigDecimal("100.00"), tMinus10);
        saveLedgerAt(clientId, walletId, currency, LedgerEntryType.DEBIT,  new BigDecimal("30.00"),  tMinus3);
        saveLedgerAt(clientId, walletId, currency, LedgerEntryType.EXPIRY, new BigDecimal("70.00"),  tMinus1);

        Instant result = schedulerRepo.findLastActivityAt(
                clientId, currency, walletId,
                EnumSet.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT,
                           LedgerEntryType.RESERVE, LedgerEntryType.RETURN_CREDIT));

        // Must return tMinus3 (DEBIT), not tMinus1 (EXPIRY which is non-activity).
        assertThat(result).isCloseTo(tMinus3, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void findLastActivityAt_noActivity_returnsNull() {
        List<RewardWallet> wallets = rewardWalletRepository.findAll();
        assumeTrue(!wallets.isEmpty(), "needs at least one existing reward wallet");
        RewardWallet w = wallets.get(0);

        // A random walletId that has no ledger rows → must return null.
        assertThat(schedulerRepo.findLastActivityAt(
                w.getClientId(), "itc-" + UUID.randomUUID(), UUID.randomUUID(),
                EnumSet.of(LedgerEntryType.CREDIT))).isNull();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void saveLedger(UUID clientId, UUID walletId, String currencyId,
                            LedgerEntryType type, BigDecimal amount) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(walletId)
                .entryType(type)
                .amount(amount)
                .currencyId(currencyId)
                .availableBalanceBefore(amount)
                .availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO)
                .build());
    }

    /**
     * Saves a ledger entry then patches its {@code created_at} via a native SQL UPDATE.
     * {@code @CreatedDate} (AuditingEntityListener) always overwrites any pre-set value on persist,
     * so we correct it with a direct column update after the INSERT, then clear the entity cache so
     * subsequent JPQL queries see the patched timestamp.
     */
    private void saveLedgerAt(UUID clientId, UUID walletId, String currencyId,
                              LedgerEntryType type, BigDecimal amount, Instant createdAt) {
        LedgerEntry entry = ledgerEntryRepository.saveAndFlush(LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(walletId)
                .entryType(type)
                .amount(amount)
                .currencyId(currencyId)
                .availableBalanceBefore(amount)
                .availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO)
                .build());
        // Patch created_at directly; @CreatedDate made it "now" — we need a specific past instant.
        entityManager.createNativeQuery(
                        "UPDATE ledger_entries SET created_at = :ts WHERE id = :id")
                .setParameter("ts", java.sql.Timestamp.from(createdAt))
                .setParameter("id", entry.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();  // evict cached entity so the @Query sees the patched timestamp
    }
}
