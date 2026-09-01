# Balance-Expiration Blocking Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two blocking adversarial findings on `features/reward-balance-expiration` — tenant-wide leakage of expiry notifications, and expiring balances of wallets that became active after the warning.

**Architecture:** A new `WalletNotificationRecipientResolver` maps a wallet to explicit recipient user IDs (INDIVIDUAL→owner, COMPANY→that company's PARTNER_ADMINs); both expiry producers use it and skip publishing when no recipient resolves (an empty list is a tenant-wide broadcast in `NotificationDispatcher`). The expire path re-checks live last-activity under the wallet lock for INACTIVITY policies and cancels a stale notice instead of debiting.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring Data JPA, JUnit 5 + Mockito + AssertJ, Micrometer, Testcontainers (integration).

## Global Constraints

- NO changes to `event/NotificationEvent.java`, `service/NotificationDispatcher.java`, Flyway migrations, or the OpenAPI contract (`contracts/endpoints/balance-expiration.yaml`).
- NO edit to the blueprint feature spec (`../tenxengage-blueprint/features/reward-balance-expiration/spec.md`).
- Inactivity revalidation applies to `ExpirationMode.INACTIVITY` only; `FIXED_DATE` is unchanged.
- An empty recipient list MUST NOT be published as a directed event. Warn phase: leave the notice `SCHEDULED` (don't expire). Expire phase: the debit has already committed — skip only the notification.
- All queries stay tenant-scoped (bind `clientId`).
- `PARTNER_ADMIN` is the exact `base_role_name` string. `ACTIVITY_ENTRY_TYPES` = `{CREDIT, DEBIT, RESERVE, RETURN_CREDIT}` (already defined in `BalanceExpiryBatchService`).
- Run unit tests with `./gradlew test --tests "<Class>"`; integration tests (`@Tag("integration")`) with `./gradlew integrationTest --tests "<Class>"` (Docker must be up: `docker compose up -d`).

---

## File Structure

- **Create** `src/main/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolver.java` — sole responsibility: wallet → recipient user IDs.
- **Create** `src/test/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolverTest.java`
- **Modify** `src/main/java/com/tenxengage/app/repository/UserRepository.java` — add `findActivePartnerAdminsByCompany`.
- **Modify** `src/main/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepository.java` — add `findLastActivityAt`.
- **Modify** `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java` — inject resolver; use it in warn + expire; skip-publish-on-empty; add inactivity revalidation.
- **Modify** `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryNoticeService.java` — inject resolver + wallet repo; resolve per wallet in cancellation; skip-publish-on-empty.
- **Modify** `src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java`, `BalanceExpiryNoticeServiceTest.java`, `src/test/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepositoryLocalIT.java`, `src/test/java/com/tenxengage/app/service/redemption/BalanceExpirationLifecycleIT.java`.

---

### Task 1: WalletNotificationRecipientResolver + recipient query

**Files:**
- Create: `src/main/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolver.java`
- Create: `src/test/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolverTest.java`
- Modify: `src/main/java/com/tenxengage/app/repository/UserRepository.java`

**Interfaces:**
- Produces: `WalletNotificationRecipientResolver.resolve(RewardWallet wallet) : List<UUID>`; `UserRepository.findActivePartnerAdminsByCompany(UUID clientId, UUID partnerCompanyId) : List<User>`.
- Consumes: `RewardWallet.getWalletType()/getUserId()/getPartnerCompanyId()/getClientId()`, `WalletType.INDIVIDUAL`/`WalletType.COMPANY`, `User.getId()`.

- [ ] **Step 1: Add the repository method.** In `UserRepository.java`, after `findByClientIdAndPartnerCompanyId` (the last method), add:

```java
/**
 * Active PARTNER_ADMIN users of a single partner company within a tenant.
 * Used to scope COMPANY-wallet balance-expiry notifications to the owning company's admins.
 */
@Query("""
    SELECT DISTINCT u FROM User u
    JOIN u.clientRole cr
    WHERE u.clientId = :clientId
    AND u.partnerCompanyId = :partnerCompanyId
    AND cr.baseRoleName = 'PARTNER_ADMIN'
    AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE
    """)
List<User> findActivePartnerAdminsByCompany(@Param("clientId") UUID clientId,
                                            @Param("partnerCompanyId") UUID partnerCompanyId);
```

(Confirm `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `com.tenxengage.app.entity.User`, `java.util.List`, `java.util.UUID` are imported — `@Query`/`@Param` and most are already used in this file.)

- [ ] **Step 2: Write the failing resolver test.** Create `WalletNotificationRecipientResolverTest.java`:

```java
package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletNotificationRecipientResolverTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private WalletNotificationRecipientResolver resolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    private RewardWallet wallet(WalletType type, UUID userId, UUID partnerCompanyId) {
        return RewardWallet.builder()
                .clientId(CLIENT_ID)
                .walletType(type)
                .userId(userId)
                .partnerCompanyId(partnerCompanyId)
                .build();
    }

    @Test
    void individualWallet_returnsOwnerOnly() {
        UUID ownerId = UUID.randomUUID();
        List<UUID> result = resolver.resolve(wallet(WalletType.INDIVIDUAL, ownerId, null));
        assertThat(result).containsExactly(ownerId);
        verifyNoInteractions(userRepository);
    }

    @Test
    void companyWallet_returnsCompanyAdminIds() {
        UUID companyId = UUID.randomUUID();
        UUID admin1 = UUID.randomUUID();
        UUID admin2 = UUID.randomUUID();
        User u1 = mock(User.class);
        User u2 = mock(User.class);
        when(u1.getId()).thenReturn(admin1);
        when(u2.getId()).thenReturn(admin2);
        when(userRepository.findActivePartnerAdminsByCompany(CLIENT_ID, companyId))
                .thenReturn(List.of(u1, u2));

        List<UUID> result = resolver.resolve(wallet(WalletType.COMPANY, null, companyId));

        assertThat(result).containsExactly(admin1, admin2);
    }

    @Test
    void companyWallet_noAdmins_returnsEmpty() {
        UUID companyId = UUID.randomUUID();
        when(userRepository.findActivePartnerAdminsByCompany(CLIENT_ID, companyId))
                .thenReturn(List.of());
        assertThat(resolver.resolve(wallet(WalletType.COMPANY, null, companyId))).isEmpty();
    }

    @Test
    void individualWallet_nullOwner_returnsEmpty() {
        assertThat(resolver.resolve(wallet(WalletType.INDIVIDUAL, null, null))).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void companyWallet_nullCompany_returnsEmpty() {
        assertThat(resolver.resolve(wallet(WalletType.COMPANY, null, null))).isEmpty();
        verifyNoInteractions(userRepository);
    }
}
```

(If `RewardWallet.builder()` does not expose one of these fields, set it via the available builder/setter — confirm field names in `entity/RewardWallet.java`; `walletType`, `userId`, `partnerCompanyId`, `clientId` are present.)

- [ ] **Step 3: Run the test to confirm it fails.**

Run: `./gradlew test --tests "*WalletNotificationRecipientResolverTest"`
Expected: FAIL — `WalletNotificationRecipientResolver` does not exist (compile error).

- [ ] **Step 4: Implement the resolver.** Create `WalletNotificationRecipientResolver.java`:

```java
package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the explicit recipient user IDs for a wallet's balance-expiry notification.
 *
 * <p>Returns an empty list when no recipient can be resolved. Callers MUST NOT publish a
 * directed notification event with an empty target list: {@code NotificationDispatcher} treats
 * an empty {@code targetUserIds} as a tenant-wide role broadcast.
 */
@Component
public class WalletNotificationRecipientResolver {

    private final UserRepository userRepository;

    public WalletNotificationRecipientResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UUID> resolve(RewardWallet wallet) {
        if (wallet.getWalletType() == WalletType.INDIVIDUAL && wallet.getUserId() != null) {
            return List.of(wallet.getUserId());
        }
        if (wallet.getWalletType() == WalletType.COMPANY && wallet.getPartnerCompanyId() != null) {
            return userRepository
                    .findActivePartnerAdminsByCompany(wallet.getClientId(), wallet.getPartnerCompanyId())
                    .stream()
                    .map(User::getId)
                    .toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 5: Run the test to confirm it passes.**

Run: `./gradlew test --tests "*WalletNotificationRecipientResolverTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolver.java \
        src/test/java/com/tenxengage/app/service/redemption/WalletNotificationRecipientResolverTest.java \
        src/main/java/com/tenxengage/app/repository/UserRepository.java
git commit -m "feat(balance-expiry): wallet notification recipient resolver"
```

---

### Task 2: Use the resolver in BalanceExpiryBatchService (warn + expire)

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java`
- Test: `src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java`

**Interfaces:**
- Consumes: `WalletNotificationRecipientResolver.resolve(RewardWallet)` (Task 1).
- Produces: no signature change to public methods; constructor gains a `WalletNotificationRecipientResolver` parameter (appended last).

- [ ] **Step 1: Update the unit test setup + add failing tests.** In `BalanceExpiryBatchServiceTest.java`:

Add the field and import:
```java
import com.tenxengage.app.service.redemption.WalletNotificationRecipientResolver;
// ...
@Mock private WalletNotificationRecipientResolver recipientResolver;
```

Update `setUp()` to inject the mock and default it to a resolvable recipient (so existing warn/expire tests still publish):
```java
@BeforeEach
void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new BalanceExpiryBatchService(
            schedulerRepo, noticeRepository, ledgerEntryRepository,
            notificationEventProducer, auditLogService, meterRegistry, recipientResolver);
    lenient().when(recipientResolver.resolve(any(RewardWallet.class)))
            .thenReturn(List.of(USER_ID));
}
```
Add imports: `static org.mockito.Mockito.lenient;` and `com.tenxengage.app.entity.RewardWallet` (already imported).

Add new tests:
```java
@Test
void processWarnForWallet_unresolvedRecipients_doesNotPublishAndLeavesScheduled() {
    BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
    RewardWallet wallet = walletWithBalance(new BigDecimal("200.00"));
    when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
            .thenReturn(List.of(wallet));
    when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
            .thenReturn(List.of(activity(wallet.getId(), daysAgo(366))));
    when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
    when(noticeRepository.save(any(BalanceExpiryNotice.class))).thenAnswer(inv -> inv.getArgument(0));
    when(recipientResolver.resolve(any(RewardWallet.class))).thenReturn(List.of()); // unresolved

    service.processWarnPhaseForPolicy(policy);

    verify(notificationEventProducer, never()).publishAndConfirm(any());
    assertThat(meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", "points").count())
            .isEqualTo(1.0);
}

@Test
void processExpireForNotice_unresolvedRecipients_stillExpiresButSkipsNotification() {
    BalanceExpirationPolicy policy = fixedDatePolicy(daysAgo(1), 30); // FIXED_DATE → no revalidation
    RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
    BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), LocalDate.now(ZoneOffset.UTC).minusDays(1));
    when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
    when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            any(), any(), any(), any())).thenReturn(false);
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    when(recipientResolver.resolve(any(RewardWallet.class))).thenReturn(List.of()); // unresolved

    boolean expired = service.processExpireForNotice(notice, policy);

    assertThat(expired).isTrue();                       // debit still happens
    verify(ledgerEntryRepository).save(any(LedgerEntry.class));
    verify(notificationEventProducer, never()).publish(any()); // notification skipped
}
```

If `fixedDatePolicy(...)` / `notifiedNotice(...)` / `activity(...)` helpers are not already in the test, add minimal private helpers mirroring the existing `inactivityPolicy` / `walletWithBalance` style (use `BalanceExpirationPolicyFixtures` / `BalanceExpiryNoticeFixtures` where they exist).

- [ ] **Step 2: Run to confirm failure.**

Run: `./gradlew test --tests "*BalanceExpiryBatchServiceTest"`
Expected: FAIL — constructor arity mismatch (resolver param not yet added) / new assertions fail.

- [ ] **Step 3: Inject the resolver.** In `BalanceExpiryBatchService.java`:

Add field after `meterRegistry` (line ~73):
```java
    private final WalletNotificationRecipientResolver recipientResolver;
```
Add the constructor parameter (append last) and assignment:
```java
    public BalanceExpiryBatchService(SchedulerBalanceExpirationRepository schedulerRepo,
                                     BalanceExpiryNoticeRepository noticeRepository,
                                     LedgerEntryRepository ledgerEntryRepository,
                                     NotificationEventProducer notificationEventProducer,
                                     AuditLogService auditLogService,
                                     MeterRegistry meterRegistry,
                                     WalletNotificationRecipientResolver recipientResolver) {
        this.schedulerRepo = schedulerRepo;
        this.noticeRepository = noticeRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.notificationEventProducer = notificationEventProducer;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        this.recipientResolver = recipientResolver;
    }
```

- [ ] **Step 4: Use the resolver in the warn phase with skip-on-empty.** In `advanceToNotified`, replace `List<UUID> recipients = resolveRecipients(wallet);` (line ~311) with a guard placed BEFORE the `NotificationEvent event = ...` construction:

```java
        List<UUID> recipients = recipientResolver.resolve(wallet);
        if (recipients.isEmpty()) {
            // Directed event with empty recipients would broadcast tenant-wide — skip the warning.
            // Leave the notice SCHEDULED so it is never expired without a delivered warning (FR-09.7).
            log.warn("step=balance_expiry_recipients_unresolved phase=warn walletId={} walletType={} currencyId={}",
                    walletId, wallet.getWalletType(), currencyId);
            meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", currencyId).increment();
            return false;
        }
        NotificationEvent event = new NotificationEvent(
        // ... unchanged event construction using `recipients` ...
```

- [ ] **Step 5: Use the resolver in the expire phase with skip-on-empty.** In `processExpireForNotice`, replace `List<UUID> recipients = resolveRecipients(wallet);` (line ~503) and wrap the event build + publish so they are skipped when empty (the debit/ledger entry has already committed above — do NOT change that):

```java
        // AC-1, Rollback safety: emit BALANCE_EXPIRED afterCommit — never for a rolled-back expiry
        List<UUID> recipients = recipientResolver.resolve(wallet);
        if (recipients.isEmpty()) {
            log.warn("step=balance_expiry_recipients_unresolved phase=expire walletId={} walletType={} currencyId={}",
                    walletId, wallet.getWalletType(), currencyId);
            meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", currencyId).increment();
        } else {
            NotificationEvent expiredEvent = new NotificationEvent(
                    "BALANCE_EXPIRED",
                    clientId,
                    "Reward Balance Expired",
                    "Your reward balance of " + expiredAmount.toPlainString() + " " + currencyId + " has expired",
                    "BALANCE_EXPIRY_NOTICE",
                    notice.getId(),
                    null,
                    recipients,
                    Map.of(
                            "currencyId", currencyId,
                            "expiredAmount", expiredAmount.toPlainString(),
                            "expiredAt", now.toString(),
                            "walletId", walletId.toString(),
                            "ledgerEntryId", savedEntry.getId().toString()
                    )
            );
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notificationEventProducer.publish(expiredEvent);
                    }
                });
            } else {
                notificationEventProducer.publish(expiredEvent);
            }
        }
```

- [ ] **Step 6: Delete the now-unused private method.** Remove `private List<UUID> resolveRecipients(RewardWallet wallet) { ... }` (lines ~611-618) and its `WalletType` import if no longer used elsewhere in the file (the expire/warn code no longer references it; keep the import only if still used).

- [ ] **Step 7: Run the tests to confirm they pass.**

Run: `./gradlew test --tests "*BalanceExpiryBatchServiceTest"`
Expected: PASS (all existing tests + the 2 new ones).

- [ ] **Step 8: Commit.**

```bash
git add src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java \
        src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java
git commit -m "fix(balance-expiry): scope warn/expire notifications to wallet audience"
```

---

### Task 3: Use the resolver in BalanceExpiryNoticeService (cancellation)

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryNoticeService.java`
- Test: `src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryNoticeServiceTest.java`

**Interfaces:**
- Consumes: `WalletNotificationRecipientResolver.resolve(RewardWallet)` (Task 1); `RewardWalletRepository.findById(UUID)`.
- Produces: constructor gains `RewardWalletRepository` and `WalletNotificationRecipientResolver` params (appended).

- [ ] **Step 1: Add failing tests.** In `BalanceExpiryNoticeServiceTest.java`, update the constructor call in setup to pass mocks for `RewardWalletRepository` and `WalletNotificationRecipientResolver`, and add:

```java
@Test
void cancelPendingForPolicy_notifiedNotice_scopesCancellationToWalletAudience() {
    UUID policyId = UUID.randomUUID();
    UUID walletId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    BalanceExpiryNotice notified = notifiedCancellableNotice(walletId);
    RewardWallet wallet = RewardWallet.builder().clientId(CLIENT_ID)
            .walletType(WalletType.INDIVIDUAL).userId(ownerId).build();
    when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(eq(CLIENT_ID), eq(policyId), anyList()))
            .thenReturn(List.of(notified));
    when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
    when(recipientResolver.resolve(wallet)).thenReturn(List.of(ownerId));

    service.cancelPendingForPolicy(policyId, CLIENT_ID);

    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(notificationEventProducer).publish(captor.capture());
    assertThat(captor.getValue().targetUserIds()).containsExactly(ownerId);
}

@Test
void cancelPendingForPolicy_unresolvedRecipients_doesNotPublish() {
    UUID policyId = UUID.randomUUID();
    UUID walletId = UUID.randomUUID();
    BalanceExpiryNotice notified = notifiedCancellableNotice(walletId);
    RewardWallet wallet = RewardWallet.builder().clientId(CLIENT_ID)
            .walletType(WalletType.COMPANY).partnerCompanyId(UUID.randomUUID()).build();
    when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(eq(CLIENT_ID), eq(policyId), anyList()))
            .thenReturn(List.of(notified));
    when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
    when(recipientResolver.resolve(wallet)).thenReturn(List.of());

    service.cancelPendingForPolicy(policyId, CLIENT_ID);

    verify(notificationEventProducer, never()).publish(any());
}
```

Add `@Mock private RewardWalletRepository walletRepository;`, `@Mock private WalletNotificationRecipientResolver recipientResolver;`, the relevant imports (`RewardWallet`, `WalletType`, `RewardWalletRepository`, `WalletNotificationRecipientResolver`, `Optional`, `anyList`, `ArgumentCaptor`), and a `notifiedCancellableNotice(UUID walletId)` helper that builds a `BalanceExpiryNotice` with `status=NOTIFIED`, `notifiedAt` set, a `scheduledExpiryDate`, and `currencyId`. (`CLIENT_ID` constant: add if not present.)

- [ ] **Step 2: Run to confirm failure.**

Run: `./gradlew test --tests "*BalanceExpiryNoticeServiceTest"`
Expected: FAIL — constructor arity mismatch / new assertions fail.

- [ ] **Step 3: Inject dependencies and resolve per wallet.** In `BalanceExpiryNoticeService.java`:

Add fields + constructor params (append `RewardWalletRepository walletRepository`, `WalletNotificationRecipientResolver recipientResolver`); add imports for `RewardWallet`, `RewardWalletRepository`, `WalletNotificationRecipientResolver`, `MeterRegistry` (and inject a `MeterRegistry` only if you want the unresolved metric here — otherwise log only).

Replace the cancellation event block (the `if (wasNotified) { ... }` body, lines ~86-104) so recipients are resolved from the wallet and empty lists are skipped:

```java
            if (wasNotified) {
                RewardWallet wallet = walletRepository.findById(notice.getWalletId()).orElse(null);
                List<UUID> recipients = wallet == null ? List.of() : recipientResolver.resolve(wallet);
                if (recipients.isEmpty()) {
                    log.warn("step=balance_expiry_cancel_recipients_unresolved walletId={} noticeId={}",
                            notice.getWalletId(), notice.getId());
                } else {
                    NotificationEvent event = new NotificationEvent(
                            "BALANCE_EXPIRY_CANCELLED",
                            clientId,
                            "Reward Balance Expiry Cancelled",
                            "Your previously scheduled reward balance expiry has been cancelled",
                            "BALANCE_EXPIRY_NOTICE",
                            notice.getId(),
                            null,
                            recipients,
                            Map.of(
                                    "currencyId", notice.getCurrencyId(),
                                    "scheduledExpiryDate", notice.getScheduledExpiryDate().toString(),
                                    "walletId", notice.getWalletId().toString()
                            )
                    );
                    cancellationEvents.add(event);
                }
            }
```

Delete the private `resolveRecipients()` method (lines ~146-155).

- [ ] **Step 4: Run the tests to confirm they pass.**

Run: `./gradlew test --tests "*BalanceExpiryNoticeServiceTest"`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryNoticeService.java \
        src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryNoticeServiceTest.java
git commit -m "fix(balance-expiry): scope cancellation notifications to wallet audience"
```

---

### Task 4: Single-wallet last-activity query

**Files:**
- Modify: `src/main/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepository.java`
- Test: `src/test/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepositoryLocalIT.java`

**Interfaces:**
- Produces: `SchedulerBalanceExpirationRepository.findLastActivityAt(UUID clientId, String currencyId, UUID walletId, Collection<LedgerEntryType> activityTypes) : Instant`.

- [ ] **Step 1: Add a failing integration test.** In `SchedulerBalanceExpirationRepositoryLocalIT.java`, mirror the existing fixture setup used by its other tests (read the file's existing `@BeforeEach`/helpers first) and add:

```java
@Test
void findLastActivityAt_returnsMaxCreatedAt_overActivityTypesOnly() {
    // Arrange: seed ledger entries for one wallet+currency:
    //   CREDIT  @ T-10d, DEBIT @ T-3d, EXPIRY @ T-1d  (EXPIRY must be ignored)
    // (use the IT's existing ledger-insert helper / repository)
    Instant tMinus10 = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS);
    Instant tMinus3  = Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
    Instant tMinus1  = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
    insertLedgerEntry(clientId, walletId, "points", LedgerEntryType.CREDIT, tMinus10);
    insertLedgerEntry(clientId, walletId, "points", LedgerEntryType.DEBIT,  tMinus3);
    insertLedgerEntry(clientId, walletId, "points", LedgerEntryType.EXPIRY, tMinus1);

    Instant result = schedulerRepository.findLastActivityAt(
            clientId, "points", walletId,
            java.util.EnumSet.of(LedgerEntryType.CREDIT, LedgerEntryType.DEBIT,
                                 LedgerEntryType.RESERVE, LedgerEntryType.RETURN_CREDIT));

    assertThat(result).isCloseTo(tMinus3, within(1, java.time.temporal.ChronoUnit.SECONDS)); // not the EXPIRY at T-1d
}

@Test
void findLastActivityAt_noActivity_returnsNull() {
    assertThat(schedulerRepository.findLastActivityAt(
            clientId, "points", UUID.randomUUID(),
            java.util.EnumSet.of(LedgerEntryType.CREDIT))).isNull();
}
```

Adapt `insertLedgerEntry(...)`, `clientId`, `walletId`, and `schedulerRepository` to the names this IT already uses; if no ledger-insert helper exists, persist `LedgerEntry` via the injected `LedgerEntryRepository`/`TestEntityManager` as the other tests do.

- [ ] **Step 2: Run to confirm failure.**

Run: `./gradlew integrationTest --tests "*SchedulerBalanceExpirationRepositoryLocalIT"`
Expected: FAIL — `findLastActivityAt` does not exist (compile error).

- [ ] **Step 3: Add the query.** In `SchedulerBalanceExpirationRepository.java`, after `findLastActivityForWallets`:

```java
    // AC-9 (expiry revalidation): live last-activity for a single wallet, used under the wallet lock
    // in the expire phase to detect a notice whose inactivity clock moved after the warning.
    @Query("SELECT MAX(e.createdAt) FROM LedgerEntry e "
            + "WHERE e.clientId = :clientId AND e.currencyId = :currencyId "
            + "AND e.rewardWalletId = :walletId AND e.entryType IN :activityTypes")
    Instant findLastActivityAt(@Param("clientId") UUID clientId,
                               @Param("currencyId") String currencyId,
                               @Param("walletId") UUID walletId,
                               @Param("activityTypes") Collection<LedgerEntryType> activityTypes);
```

Add `import java.time.Instant;` if not present (`Collection`, `LedgerEntryType`, `UUID`, `@Query`, `@Param` already imported).

- [ ] **Step 4: Run the test to confirm it passes.**

Run: `./gradlew integrationTest --tests "*SchedulerBalanceExpirationRepositoryLocalIT"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepository.java \
        src/test/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepositoryLocalIT.java
git commit -m "feat(balance-expiry): single-wallet last-activity query"
```

---

### Task 5: Revalidate inactivity before the irreversible debit

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java`
- Test: `src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java`

**Interfaces:**
- Consumes: `SchedulerBalanceExpirationRepository.findLastActivityAt(...)` (Task 4); `ExpirationMode.INACTIVITY`; `ExpiryNoticeStatus.CANCELLED`; `BalanceExpiryNotice.setCancelledAt(Instant)`.

- [ ] **Step 1: Add failing unit tests.** In `BalanceExpiryBatchServiceTest.java` add:

```java
@Test
void processExpireForNotice_inactivity_postWarningActivity_cancelsNoticeAndSkipsDebit() {
    BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
    RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
    LocalDate scheduled = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), scheduled);
    when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
    when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            any(), any(), any(), any())).thenReturn(false);
    // Fresh activity 5 days ago → freshExpiry = today+360, which != scheduled (yesterday) → stale
    when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
            .thenReturn(daysAgo(5));

    boolean expired = service.processExpireForNotice(notice, policy);

    assertThat(expired).isFalse();
    assertThat(notice.getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("100.00"); // untouched
}

@Test
void processExpireForNotice_inactivity_stillInactive_expires() {
    BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
    RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
    // scheduled = lastActivity(366d ago) + 365 = yesterday
    LocalDate scheduled = daysAgo(366).atZone(ZoneOffset.UTC).toLocalDate().plusDays(365);
    BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), scheduled);
    when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
    when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            any(), any(), any(), any())).thenReturn(false);
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
            .thenReturn(daysAgo(366)); // unchanged → freshExpiry == scheduled

    boolean expired = service.processExpireForNotice(notice, policy);

    assertThat(expired).isTrue();
    verify(ledgerEntryRepository).save(any(LedgerEntry.class));
}

@Test
void processExpireForNotice_inactivity_noLiveActivity_skipsDebit() {
    BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
    RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
    BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), LocalDate.now(ZoneOffset.UTC).minusDays(1));
    when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
    when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            any(), any(), any(), any())).thenReturn(false);
    when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
            .thenReturn(null);

    boolean expired = service.processExpireForNotice(notice, policy);

    assertThat(expired).isFalse();
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
}
```

(The `processExpireForNotice_unresolvedRecipients_stillExpiresButSkipsNotification` test from Task 2 uses a FIXED_DATE policy specifically so it skips revalidation — keep it that way.)

- [ ] **Step 2: Run to confirm failure.**

Run: `./gradlew test --tests "*BalanceExpiryBatchServiceTest"`
Expected: FAIL — the post-activity test expires (no revalidation yet) / `findLastActivityAt` stub unused.

- [ ] **Step 3: Implement revalidation.** In `processExpireForNotice`, insert this block AFTER the zero-balance check (`if (expiredAmount.compareTo(BigDecimal.ZERO) <= 0) { ... return false; }`, line ~462) and BEFORE `Instant now = Instant.now();` (line ~464):

```java
        // Fix: revalidate the inactivity condition under the lock before the irreversible debit.
        // The notice's scheduledExpiryDate was computed in an earlier warn phase; activity after the
        // warning moves the inactivity clock forward and invalidates it. FIXED_DATE is activity-independent.
        if (policy.getExpirationMode() == ExpirationMode.INACTIVITY) {
            Instant freshLastActivity = schedulerRepo.findLastActivityAt(
                    clientId, currencyId, walletId, ACTIVITY_ENTRY_TYPES);
            if (freshLastActivity == null) {
                // Anomaly: a NOTIFIED notice exists but no activity is visible now (append-only ledger).
                // Do not destroy balance on inconsistent data.
                log.warn("step=balance_expiry_revalidation_no_activity walletId={} currencyId={} noticeId={}",
                        walletId, currencyId, notice.getId());
                return false;
            }
            LocalDate freshExpiryDate = freshLastActivity.atZone(ZoneOffset.UTC).toLocalDate()
                    .plusDays(policy.getInactivityDays());
            if (!freshExpiryDate.equals(notice.getScheduledExpiryDate())) {
                // Activity moved the inactivity clock — this notice is stale. Cancel it; a future
                // sweep re-warns for the new date if the wallet goes inactive again.
                notice.setStatus(ExpiryNoticeStatus.CANCELLED);
                notice.setCancelledAt(Instant.now());
                noticeRepository.save(notice);
                log.info("step=balance_expiry_revalidation_stale walletId={} currencyId={} scheduledExpiryDate={} freshExpiryDate={}",
                        walletId, currencyId, notice.getScheduledExpiryDate(), freshExpiryDate);
                meterRegistry.counter("balance_expiry.revalidation_stale.total", "currencyId", currencyId).increment();
                return false;
            }
        }
```

Confirm imports: `com.tenxengage.app.entity.enums.ExpirationMode` and `java.time.ZoneOffset` (ZoneOffset already used at line ~381; ExpirationMode is used in `bulkLastActivity` so already imported).

- [ ] **Step 4: Run the tests to confirm they pass.**

Run: `./gradlew test --tests "*BalanceExpiryBatchServiceTest"`
Expected: PASS (all).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java \
        src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java
git commit -m "fix(balance-expiry): revalidate inactivity under lock before expiry"
```

---

### Task 6: Integration regressions + full feature suite

**Files:**
- Test: `src/test/java/com/tenxengage/app/service/redemption/BalanceExpirationLifecycleIT.java`

**Interfaces:**
- Consumes: the full wired stack (resolver, queries, revalidation) from Tasks 1-5.

- [ ] **Step 1: Add the two regression integration tests Codex requested.** Read `BalanceExpirationLifecycleIT.java`'s existing setup (tenant/client/wallet/policy/user fixtures, how it triggers the sweep, and how it asserts on notifications/notices) and mirror it. Add:

```java
@Test
void inactivityExpiry_walletActiveAfterWarning_isNotExpired() {
    // Arrange: enable an INACTIVITY policy; create an INDIVIDUAL wallet whose last activity is old
    // enough to be warned; run the warn sweep so a NOTIFIED notice with a due scheduledExpiryDate exists.
    // Then record NEW activity (a CREDIT) for the wallet AFTER the warning.
    // Act: run the expire sweep (runExpirySweep / processExpirePhaseForPolicy).
    // Assert: no EXPIRY ledger entry for the wallet; availableBalance unchanged;
    //         the notice status is CANCELLED.
}

@Test
void companyWalletExpiry_notifiesOnlyOwningCompanyAdmins() {
    // Arrange: two partner companies (A, B) each with a PARTNER_ADMIN + a PARTNER_SELLER;
    // a COMPANY wallet owned by company A with a due, NOTIFIED notice.
    // Act: run the expire sweep.
    // Assert: the BALANCE_EXPIRED notification targets only company A's PARTNER_ADMIN — NOT company B's
    //         users and NOT company A's PARTNER_SELLER. Assert via the notifications created by
    //         NotificationDispatcher for these users (mirror how other IT notification assertions read
    //         the notification_repository), or by capturing the published NotificationEvent's targetUserIds.
}
```

Implement both bodies using the IT's existing fixture/sweep/assertion helpers. If the IT consumes events asynchronously, assert on the persisted `Notification` rows per user (as the existing event ITs do); otherwise assert on the captured event's `targetUserIds`.

- [ ] **Step 2: Run the integration tests.**

Run: `./gradlew integrationTest --tests "*BalanceExpirationLifecycleIT"`
Expected: PASS (existing 9 + 2 new).

- [ ] **Step 3: Run the full feature suite (unit + integration).**

```bash
./gradlew test --tests "*BalanceExpiration*" --tests "*BalanceExpiry*" --tests "*BalanceBreakage*" \
    --tests "*SchedulerBalanceExpiration*" --tests "*NotificationEventProducer*" \
    --tests "*WalletNotificationRecipientResolver*" --tests "*AuditLogRepositoryFindFiltered*"
./gradlew integrationTest --tests "*BalanceExpiration*" --tests "*SchedulerBalanceExpiration*" \
    --tests "*AuditLogRepositoryFindFiltered*"
```
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 4: Commit.**

```bash
git add src/test/java/com/tenxengage/app/service/redemption/BalanceExpirationLifecycleIT.java
git commit -m "test(balance-expiry): regressions for stale-expiry + company audience scoping"
```

- [ ] **Step 5: Re-run the adversarial ready-check step** to confirm the blocking findings clear:

```bash
# from the chat: /ready-check adversarial-review
```
Expected: Step 5 PASSED (0 blocking).

---

## Self-Review

- **Spec coverage:** Finding 1 → Tasks 1-3 (resolver + both producers + skip-on-empty) + Task 6 company-audience IT. Finding 2 → Tasks 4-5 (query + revalidation) + Task 6 stale-expiry IT. Decisions (local resolver / PARTNER_ADMIN-only / cancel-silently) and the 4 review corrections (compare to notice's date; null→skip; unresolved→skip-publish + metric; verified assumptions) are all implemented. ✓
- **Placeholder scan:** Production code is fully specified. Integration-test bodies (Task 6) are intentionally scenario-described because they depend on `BalanceExpirationLifecycleIT`'s existing fixtures, which the implementer must read and mirror — flagged explicitly, not a silent gap. ✓
- **Type consistency:** `resolve(RewardWallet):List<UUID>`, `findActivePartnerAdminsByCompany(UUID,UUID):List<User>`, `findLastActivityAt(UUID,String,UUID,Collection<LedgerEntryType>):Instant`, `ExpiryNoticeStatus.CANCELLED`, `setCancelledAt(Instant)`, metric `balance_expiry.recipients_unresolved.total` / `balance_expiry.revalidation_stale.total` — used consistently across tasks. ✓
