# Retire the Wallet Rail from Distribution — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Distribution offers two rails — digital gift card and bank transfer — and both actually work. The wallet rail is no longer offered or accepted for new distributions.

**Architecture:** Refuse forward, read backward. `WALLET_CREDIT` is removed from the two places that *offer* it and the one that *accepts* it, but the enum constant, its settlement path, its notification copy and its null-safe reads all stay — three such distributions already exist in the dev database, and deleting the constant would make them unreadable.

**Tech Stack:** Java 21, Spring Boot, JUnit 5 + Mockito + AssertJ. Frontend: React 18, TypeScript, Vitest.

**Spec:** No design doc. The decision is recorded here: @pushpendra, 2026-08-26 — *"we dont need wallet rail in distribution"*, following *"in distribution - we need only two rails - digital gift card and bank transfer"*.

## Global Constraints

- **Worktrees:** `../tenxengage-backend-company-distribution` and `../tenxengage-frontend-company-distribution`, branch `features/company-distribution-store`.
- **Never `git add -A`.** Stage by explicit path.
- **Backend baseline: 1758 passing, 0 failures.** Frontend settings/client-admin: 74 passing.
- Full-suite runs take 8–30 minutes and the duration is not a signal. Targeted tests per task; full suite once at the end.
- **`integrationTest` runs against the LIVE dev database.** Do not run it.

### Verified before writing this plan

| Fact | How |
|---|---|
| **3 `WALLET_CREDIT` distributions already exist** (vs 2 gift card, 1 bank transfer) | queried `GET /api/v1/redemption/distribution` against the running dev app |
| The rail is *offered* in exactly 2 places and *accepted* in 1 | `grep` across both repos |
| 7 other backend references exist only to read or settle old rows | inspected each |

### Retiring the rail forces a second decision

`railAvailable()` returns true when the XTRM flag is on **or** the rail is not one of the two XTRM ones — a clause only the wallet rail ever satisfied. Retire it and that expression collapses to the flag, which is `false` in every environment. Distribution would stop working entirely.

So Task 4 turns the flag on. Approved by @pushpendra, 2026-08-26. It is a separate decision from retiring a rail, and is called out rather than folded in silently.

### The consequence, stated once

A seller who is platform-bound, or not yet enrolled, now has **no rail that can reach them**. The wallet rail was the only one that needed nothing of the seller. Every ineligibility message loses its "use Wallet Transfer instead" suffix because there is no longer anything to point at. This follows from the decision; it is not a defect to fix later.

---

## File Structure

**Backend — modified**

| File | Change |
|---|---|
| `service/CompanyDistributionService.java:317` | `case WALLET_CREDIT` → refuse with `UNSUPPORTED_RAIL` |
| `service/DistributionRecipientService.java:204` | remove the `case WALLET_CREDIT` that returns eligible; drop 5 copy suffixes |
| `resources/application-local.yml:38` | `xtrm-payout-rails-enabled` default → `true` |

**Backend — deliberately untouched** (they keep the 3 existing rows readable and settleable)

`entity/enums/DistributionRail.java`, `service/WalletCreditSettlementService.java`, `service/DistributionSettlementListener.java`, `service/CompanyDistributionQueryService.java`, `service/DistributionNotificationService.java`, `service/CompanyDistributionDispatcher.java`, `entity/CompanyDistributionItem.java`

**Frontend — modified**

| File | Change |
|---|---|
| `pages/distribution/DistributionStorePage.tsx:27-46` | drop the RAILS entry; fix the default-rail fallback |
| `types/company-distribution.types.ts:10` | keep the union member, document why |
| `config/redemptionFeatures.ts:23` | `XTRM_PAYOUT_RAILS_ENABLED` → `true` |

---

## Task 1: Refuse the wallet rail on create

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/CompanyDistributionService.java:317-324`
- Test: `src/test/java/com/tenxengage/app/service/CompanyDistributionWalletRailRetiredTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `POST /redemption/distribution` with `rail=WALLET_CREDIT` now throws `BusinessRuleException("UNSUPPORTED_RAIL", ...)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/CompanyDistributionWalletRailRetiredTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.entity.enums.DistributionRail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wallet rail is retired from distribution: no longer offered, no longer accepted.
 *
 * <p>The enum constant stays. Three WALLET_CREDIT distributions already exist in the dev database, and
 * {@code company_distributions.rail} stores the constant's name — deleting it would make that history
 * unreadable and break the settlement path for any item still in flight.</p>
 */
class CompanyDistributionWalletRailRetiredTest {

    @Test
    void theConstantStillExistsSoOldRowsRemainReadable() {
        // Reading a stored 'WALLET_CREDIT' must keep working. This is the guard against someone tidying
        // the enum and silently breaking distribution history.
        assertThat(DistributionRail.valueOf("WALLET_CREDIT")).isNotNull();
    }

    @Test
    void bothRemainingRailsGoThroughTheVendor() {
        // Once the wallet rail is not offered, every rail a caller can pick is an XTRM payout. Anything
        // relying on a non-vendor rail existing is now relying on history only.
        assertThat(DistributionRail.GIFT_CARD.isVendorPayout()).isTrue();
        assertThat(DistributionRail.BANK_TRANSFER.isVendorPayout()).isTrue();
    }

    @Test
    void theWalletRailIsStillMarkedNonVendorForOldRows() {
        assertThat(DistributionRail.WALLET_CREDIT.isVendorPayout()).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it passes**

Run: `./gradlew test --tests "com.tenxengage.app.service.CompanyDistributionWalletRailRetiredTest"`
Expected: PASS. These three pin what must **not** change; they are a regression guard, not a red test. The behavioural change is covered by Task 2's tests, which do start red.

- [ ] **Step 3: Refuse the rail in `railTarget`**

Replace the `case WALLET_CREDIT` block at `CompanyDistributionService:317-324` with:

```java
            case WALLET_CREDIT -> {
                // Retired 2026-08-26: distribution offers gift card and bank transfer only. The constant
                // and its settlement path remain so existing WALLET_CREDIT distributions stay readable and
                // any in-flight item still settles — but nothing new may be created on it.
                throw new BusinessRuleException("UNSUPPORTED_RAIL",
                        "Wallet transfer is no longer available for distributions.");
            }
```

Leave `CASH_CURRENCY` and the `item == null` branch in `validateAmount` alone: both are still reached when an existing wallet-rail item settles.

- [ ] **Step 4: Run the distribution service tests**

Run: `./gradlew test --tests "com.tenxengage.app.service.CompanyDistributionServiceTest"`
Expected: FAIL for any case that creates a `WALLET_CREDIT` distribution. Those cases are now asserting retired behaviour — change each to assert the refusal, or move it to `GIFT_CARD` if the case is about something other than the rail. Read each before changing it; do not delete a case that is testing amount validation or idempotency merely because it happens to use this rail.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/CompanyDistributionService.java \
        src/test/java/com/tenxengage/app/service/CompanyDistributionWalletRailRetiredTest.java \
        src/test/java/com/tenxengage/app/service/CompanyDistributionServiceTest.java
git commit -m "feat(distribution): retire the wallet rail — refuse new, keep old readable"
```

---

## Task 2: Stop offering the rail, and drop the fallback copy

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/DistributionRecipientService.java:44-45, 182, 189, 198, 202, 204-207`
- Test: `src/test/java/com/tenxengage/app/service/DistributionRecipientWalletRailRetiredTest.java`

**Interfaces:**
- Consumes: Task 1's refusal.
- Produces: `listRecipients(..., WALLET_CREDIT)` reports every seller ineligible; the five ineligibility strings no longer end in "use Wallet Transfer instead".

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/DistributionRecipientWalletRailRetiredTest.java`:

```java
package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.DistributionRecipientResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The wallet rail is no longer offered, and nothing points at it any more.
 *
 * <p>It was the fallback in five ineligibility messages precisely because it needed nothing of the seller.
 * With it retired, a seller who is platform-bound or un-enrolled has no reachable rail at all — so the
 * messages must state the problem rather than name a remedy that no longer exists.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientWalletRailRetiredTest {

    @Mock private UserRepository userRepository;
    @Mock private PartnerRedemptionRepository profileRepository;
    @Mock private PartnerLinkedBankRepository linkedBankRepository;
    @Mock private XtrmCredentialsResolver credentialsResolver;

    private DistributionRecipientService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DistributionRecipientService(userRepository, profileRepository, linkedBankRepository,
                credentialsResolver, true);

        User seller = new User();
        seller.setId(SELLER_ID);
        seller.setEmail("seller@acme.test");
        when(userRepository.findActiveSellersOfCompany(CLIENT_ID, COMPANY_ID)).thenReturn(List.of(seller));
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(true);
        when(credentialsResolver.companyIssuerAccountNumber(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of("SPN26241004"));
    }

    private DistributionRecipientResponse only(DistributionRail rail) {
        List<DistributionRecipientResponse> out = service.listRecipients(CLIENT_ID, COMPANY_ID, rail);
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    @Test
    void theWalletRailReachesNobody() {
        PartnerRedemption enrolled = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26241031")
                .enrolledIssuerAccountNumber("SPN26241004")
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(enrolled));

        // Even a fully set-up seller: the rail is retired, so it reaches nobody.
        assertThat(only(DistributionRail.WALLET_CREDIT).eligible()).isFalse();
    }

    @Test
    void noReasonPointsAtTheRetiredRail() {
        PartnerRedemption notEnrolled = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(notEnrolled));

        // Naming a remedy that no longer exists is worse than naming none.
        assertThat(only(DistributionRail.GIFT_CARD).ineligibleReason())
                .doesNotContainIgnoringCase("wallet transfer");
    }

    @Test
    void theUnenrolledReasonStillSaysWhatIsWrong() {
        PartnerRedemption notEnrolled = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(notEnrolled));

        assertThat(only(DistributionRail.GIFT_CARD).ineligibleReason())
                .containsIgnoringCase("no payout profile");
    }

    @Test
    void thePlatformBoundReasonStillSaysWhatIsWrong() {
        PartnerRedemption legacy = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26240089")
                .enrolledIssuerAccountNumber("SPN26237883")
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(legacy));

        assertThat(only(DistributionRail.GIFT_CARD).ineligibleReason())
                .containsIgnoringCase("cannot receive")
                .doesNotContainIgnoringCase("wallet transfer");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipientWalletRailRetiredTest"`
Expected: FAIL — `theWalletRailReachesNobody` (the rail still returns eligible) and the two "does not contain wallet transfer" assertions.

- [ ] **Step 3: Stop offering the rail**

Replace the `case WALLET_CREDIT` block at `DistributionRecipientService:204-207` with:

```java
            case WALLET_CREDIT -> {
                // Retired 2026-08-26. Existing distributions on this rail still settle and still display;
                // it is simply no longer offered as a destination for new ones.
                return Eligibility.no("Wallet transfer is no longer available");
            }
```

- [ ] **Step 4: Drop the five fallback suffixes**

There is no longer a rail to point at, so each message states only the problem:

| Line | Was | Becomes |
|---|---|---|
| 45 | `"Temporarily unavailable — use Wallet Transfer instead"` | `"Temporarily unavailable"` |
| 182 | `"This seller cannot receive company payouts — use Wallet Transfer instead"` | `"This seller cannot receive company payouts"` |
| 189 | `"No payout profile yet — use Wallet Transfer instead"` | `"No payout profile yet"` |
| 198 | `"No payout profile yet — use Wallet Transfer instead"` | `"No payout profile yet"` |
| 202 | `"No bank account linked — use Wallet Transfer instead"` | `"No bank account linked"` |

Also update the class javadoc at lines 29-35, which explains that ineligible sellers are shown "so the admin sees *no payout profile — use Wallet Transfer instead*" and that this "is what makes the wallet-transfer rail useful". Both claims are now false. Replace with: ineligible sellers are still shown with a reason, so the admin knows who cannot be paid and why — but there is no longer an alternative rail for them.

- [ ] **Step 5: Run the recipient tests**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipient*"`
Expected: FAIL in `DistributionRecipientCompanyConnectionTest` and `DistributionRecipientIssuerMismatchTest` — each has a case asserting `WALLET_CREDIT` is eligible for a seller the XTRM rails refuse. Those cases documented the fallback, which no longer exists. Rewrite each to assert the rail is now refused, and keep the surrounding case (they also assert the vendor-rail behaviour, which is unchanged). `DistributionRecipientServiceRailSwitchTest` may also reference the rail; read it before changing it.

- [ ] **Step 6: Run the full backend suite**

Run: `./gradlew test`
Expected: PASS. `WalletCreditSettlementServiceTest`, `DistributionNotificationServiceTest` and `CompanyDistributionQueryServiceTest` must all still pass untouched — they cover the read and settle paths for the 3 existing rows, and any failure there means the retirement went too far.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/DistributionRecipientService.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientWalletRailRetiredTest.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientCompanyConnectionTest.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientIssuerMismatchTest.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientServiceRailSwitchTest.java
git commit -m "feat(distribution): stop offering the wallet rail, and stop pointing at it"
```

---

## Task 3: Remove the rail from the store, and fix the default

The default-rail expression currently falls back to `WALLET_CREDIT` by name when every rail is blocked. Deleting the entry without touching that line leaves the page defaulting to a rail it no longer lists.

**Files:**
- Create: `src/pages/distribution/distributionRails.ts`
- Modify: `src/pages/distribution/DistributionStorePage.tsx:27-46`
- Modify: `src/types/company-distribution.types.ts:7-10`
- Test: `src/pages/distribution/__tests__/distributionRails.test.ts`

**Why a new module.** Importing `RAILS` from `DistributionStorePage` pulls in 18 module imports —
react-router, hooks, a dozen UI components — to assert two facts about a constant array. The frontend
already shows timeouts under machine load when rendering that page, so a focused module is both a better
unit and a test that cannot fail for unrelated reasons.

**Interfaces:**
- Consumes: Task 2's backend behaviour.
- Produces: `RAILS` has two entries; `DEFAULT_RAIL` is always a listed rail.

- [ ] **Step 1: Write the failing test**

Create `src/pages/distribution/__tests__/distributionRails.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { RAILS, DEFAULT_RAIL } from "../distributionRails";

/**
 * The wallet rail is retired from the store.
 *
 * <p>DEFAULT_RAIL used to fall back to "WALLET_CREDIT" by name when every rail was blocked — with the entry
 * gone, that fallback would name a rail the page does not list. These pin that it always resolves to
 * something actually offered.</p>
 */
describe("Distribution store rails", () => {
  it("offers gift card and bank transfer only", () => {
    expect(RAILS.map((r) => r.value)).toEqual(["GIFT_CARD", "BANK_TRANSFER"]);
  });

  it("never defaults to a rail it does not offer", () => {
    expect(RAILS.map((r) => r.value)).toContain(DEFAULT_RAIL);
  });
});
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `npx vitest run src/pages/distribution/__tests__/distributionRails.test.ts`
Expected: FAIL — the module does not exist yet.

- [ ] **Step 3: Move the rails into their own module, without the wallet entry**

Create `src/pages/distribution/distributionRails.ts` holding what currently sits at
`DistributionStorePage.tsx:27-46`, minus the wallet entry, with the fallback made honest:

```ts
import type { DistributionRail } from "@/types/company-distribution.types";
import { XTRM_PAYOUT_RAILS_ENABLED } from "@/config/redemptionFeatures";

export const RAILS: { value: DistributionRail; label: string; needsXtrm: boolean }[] = [
  { value: "GIFT_CARD", label: "Gift Card", needsXtrm: true },
  { value: "BANK_TRANSFER", label: "Bank Transfer", needsXtrm: true },
];

/**
 * Land on a rail that can actually be sent, so the default path is a working one.
 *
 * Both remaining rails need XTRM, so when the payout rails are switched off there is no sendable rail at
 * all. Falling back to the first listed one is deliberate: the tabs stay browsable, the send button carries
 * the reason, and the page never names a rail it does not offer — which is what the old
 * `?? "WALLET_CREDIT"` would now do.
 */
export const railSendBlocked = (r: DistributionRail) =>
  RAILS.some((x) => x.value === r && x.needsXtrm) && !XTRM_PAYOUT_RAILS_ENABLED;

export const DEFAULT_RAIL: DistributionRail =
  RAILS.find((r) => !railSendBlocked(r.value))?.value ?? RAILS[0].value;
```

Then delete those declarations from `DistributionStorePage.tsx` and import them instead:

```tsx
import { RAILS, DEFAULT_RAIL, railSendBlocked } from "./distributionRails";
```

- [ ] **Step 4: Keep the type member, and say why**

In `types/company-distribution.types.ts`, leave `"WALLET_CREDIT"` in the union and replace the comment:

```ts
/**
 * `WALLET_CREDIT` is retired — the store no longer offers it and the API refuses new distributions on it.
 * The member stays because existing distributions carry that rail and history still renders them.
 */
export type DistributionRail = "GIFT_CARD" | "BANK_TRANSFER" | "WALLET_CREDIT";
```

- [ ] **Step 5: Run the distribution tests**

Run: `npx vitest run src/pages/distribution`
Expected: PASS. `DistributionStorePage.test.tsx` and `DistributionStorePage.railsDisabled.test.tsx` both exist; the second is specifically about the disabled-rails path and is the most likely to assert the old default. Read each failure before changing it.

- [ ] **Step 6: Typecheck and commit**

Run: `npx tsc --noEmit`
Expected: no output.

```bash
git add src/pages/distribution/DistributionStorePage.tsx \
        src/types/company-distribution.types.ts \
        src/pages/distribution/__tests__/DistributionStorePage.walletRailRetired.test.tsx
git commit -m "feat(distribution): remove the wallet rail from the store"
```

---

## Task 4: Turn the XTRM payout rails on

**Without this the feature is dead.** `railAvailable()` is `xtrmPayoutRailsEnabled || rail is not GIFT_CARD/BANK_TRANSFER` — the second clause was only ever true for the wallet rail. Retire that rail and the expression collapses to the flag alone, which is `false` everywhere. Every distribution would return `RAIL_UNAVAILABLE`.

The flag was switched off when company→seller payouts did not work. They work now: the mechanism was confirmed by the vendor and verified against the sandbox on 2026-08-26 — a company-created user reports `Connected` to its company, and the platform still reaches that same user.

**Files:**
- Modify: `src/main/resources/application-local.yml:38` (backend worktree)
- Modify: `src/config/redemptionFeatures.ts:23` (frontend worktree)
- Test: `src/test/java/com/tenxengage/app/service/DistributionRecipientServiceRailSwitchTest.java` — already exists and pins this behaviour

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: gift card and bank transfer are submittable.

- [ ] **Step 1: Confirm the switch is what is blocking**

Run: `grep -rn "xtrm-payout-rails-enabled" src/main/resources/`
Expected: `application-local.yml` and `application-localtest.yml`, both defaulting to `false`.

- [ ] **Step 2: Flip the backend default**

In `application-local.yml`, change the default from `false` to `true`:

```yaml
    # Company→seller payouts work: a seller created under their own company's credentials is Connected to
    # it, verified against the sandbox 2026-08-26. The wallet rail that used to carry distribution while
    # this was off has been retired, so leaving it false now disables distribution entirely.
    xtrm-payout-rails-enabled: ${XTRM_PAYOUT_RAILS_ENABLED:true}
```

**Leave `application-localtest.yml` at `false`.** That profile runs against the stub, and `DistributionRecipientServiceRailSwitchTest` constructs the service with the flag explicitly, so tests are unaffected either way — but flipping a test profile to match production behaviour hides the off case rather than testing it.

- [ ] **Step 3: Flip the frontend constant**

In `src/config/redemptionFeatures.ts`, change line 23:

```ts
export const XTRM_PAYOUT_RAILS_ENABLED: boolean = true;
```

Update the comment above it, which currently says to flip this "when XTRM is ready" — it is.

- [ ] **Step 4: Confirm the rail switch tests still pass both ways**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipientServiceRailSwitchTest"`
Expected: PASS. That suite passes the flag into the constructor per case, so it still covers the off path — which now means "distribution disabled" rather than "wallet rail only". If a case asserts the wallet rail works while the flag is off, it is asserting retired behaviour and needs rewriting to assert `RAIL_UNAVAILABLE`.

- [ ] **Step 5: Frontend tests and typecheck**

Run: `npx vitest run src/pages/distribution` then `npx tsc --noEmit`
Expected: PASS. `DistributionStorePage.railsDisabled.test.tsx` asserts the disabled-rail UI; with the flag now `true` it may need the flag stubbed to keep testing the off state deliberately rather than incidentally.

- [ ] **Step 6: Commit both repos**

```bash
# backend worktree
git add src/main/resources/application-local.yml         src/test/java/com/tenxengage/app/service/DistributionRecipientServiceRailSwitchTest.java
git commit -m "feat(distribution): turn the XTRM payout rails on"

# frontend worktree
git add src/config/redemptionFeatures.ts src/pages/distribution/__tests__/DistributionStorePage.railsDisabled.test.tsx
git commit -m "feat(distribution): turn the XTRM payout rails on"
```

---

## After the plan

**What deliberately still works:** the 3 existing `WALLET_CREDIT` distributions render in history, settle if any item is still in flight, and notify their recipients. `WalletCreditSettlementService` and the notification copy are untouched for exactly that reason.

**What is now unreachable:** a seller who is platform-bound, or not yet enrolled, cannot receive a distribution on any rail. The recipient list will show them ineligible with no alternative. That is the accepted consequence of the decision, not an open defect.

**Not addressed here:** company wallet funding still credits the internal ledger only (D-8, dropped 2026-08-26 — funding is manual for testing). The internal balance and the XTRM balance will differ; XTRM's is what gates a payout.
