# Company-Scoped Seller Enrollment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrol every seller at XTRM under their own partner company rather than under the platform, so that company-remitted distributions can actually reach them.

**Architecture:** A seller is bound at XTRM to whichever account called `Register/CreateUser` for them, and that binding is permanent — XTRM refuses a second user with the same email, so it cannot be redone. Enrollment therefore moves to the seller's own company's credentials, the issuer is recorded on the profile so a payout can check it, and enrollment **defers** rather than falling back to the platform when the company is not connected yet.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, Flyway, JUnit 5 + Mockito + AssertJ.

**Spec:** [`2026-08-24-company-beneficiary-provisioning-design.md`](2026-08-24-company-beneficiary-provisioning-design.md) §8.1 — read it before Task 1. This plan implements the follow-on work that section identifies.

## Global Constraints

- **Worktree:** `../tenxengage-backend-company-distribution`, branch `features/company-distribution-store`. Not `../tenxengage-backend` — a failed checkout there silently fakes success.
- **Never `git add -A`.** `autocrlf=true` with no `.gitattributes` makes ~1050 files look modified. Stage by explicit path.
- **Tests:** `./gradlew test --tests "com.tenxengage.app.<...>"`. Baseline is **1732 passing, 0 failures**. Full-suite runs take 8–30 minutes and the duration is not a useful signal; run targeted tests per task and the full suite at the checkpoints this plan names.
- **`integrationTest` is a different task against the LIVE dev database.** Do not run it here.
- **Migration version is V59.** Confirm with `ls src/main/resources/db/migration/ | sort -V | tail -2` before writing.
- **Unit tests build the schema from entities, not migrations.** Entity and SQL must agree; the SQL is untested by `./gradlew test`.
- Secrets never reach logs, exception messages, or API responses.

### The three decisions this plan implements

| # | Decision |
|---|---|
| E-1 | The **XTRM issuer account number** is recorded on `partner_redemption` at enrollment. Existing rows stay `NULL`, which means "enrolled by the platform before this existed" — no environment-specific literal is needed to say so. |
| E-2 | Sellers already enrolled under the platform **cannot be migrated** ("Email Already Exists" is final). They get a distinct ineligibility reason on vendor rails and keep `WALLET_CREDIT`. This is a permanent limitation, not a defect. |
| E-3 | Enrollment **never falls back to the platform** for a seller who belongs to a partner company. If the company is not `CONNECTED`, enrollment defers. |

### Why E-3 is worth its cost

Deferring blocks a new seller's personal redemption until an admin connects their company. Enrolling under the platform instead would let that redemption happen today and exclude the seller from company distributions **forever**. The two failure modes are not symmetric: one is a setup step, the other is unrecoverable. Prefer the reversible failure.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `db/migration/V59__record_enrollment_issuer.sql` | `enrolled_issuer_account_number` on `partner_redemption`; legacy rows stay NULL |
| `service/xtrm/SellerEnrollmentIssuerResolver.java` | Which XTRM account should enrol this seller, and is it ready |

**Modified**

| File | Change |
|---|---|
| `entity/xtrm/PartnerRedemption.java` | `enrolledIssuerAccountNumber` field |
| `service/xtrm/XtrmApiClient.java` + `Impl` + `Stub` | `createUser` credentials overload |
| `service/xtrm/XtrmEnrollmentService.java` | Enrol as the seller's company; defer when not ready; record the issuer |
| `service/DistributionRecipientService.java` | Ineligibility reason for a platform-bound seller |
| `service/xtrm/XtrmCredentialsResolver.java` | `companyIssuerAccountNumber` — reads the SPN without decrypting |

---

## Task 1: Record which account enrolled each seller

Without this, a platform-bound seller and a company-bound seller are indistinguishable, and nothing downstream can refuse the first.

**Files:**
- Create: `src/main/resources/db/migration/V59__record_enrollment_issuer.sql`
- Modify: `src/main/java/com/tenxengage/app/entity/xtrm/PartnerRedemption.java`
- Test: `src/test/java/com/tenxengage/app/entity/xtrm/PartnerRedemptionIssuerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `PartnerRedemption.getEnrolledIssuerAccountNumber()` / `setEnrolledIssuerAccountNumber(String)`, and `PartnerRedemption.isEnrolledUnder(String issuerAccountNumber)` returning `boolean`.

- [ ] **Step 1: Confirm V59 is free**

Run: `ls src/main/resources/db/migration/ | sort -V | tail -2`
Expected: the highest is `V58__company_admin_and_xtrm_account_amendments.sql`. If something claims V59, use the next free number and say so in the commit message.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/tenxengage/app/entity/xtrm/PartnerRedemptionIssuerTest.java`:

```java
package com.tenxengage.app.entity.xtrm;

import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which XTRM account enrolled this seller.
 *
 * <p>XTRM binds a user to whoever created them and refuses a second user with the same email, so this is a
 * permanent fact about the row rather than something that can be corrected later. Everything that decides
 * whether a company can pay a seller reads it.</p>
 */
class PartnerRedemptionIssuerTest {

    private PartnerRedemption enrolledUnder(String issuer) {
        return PartnerRedemption.builder()
                .clientId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26241022")
                .enrolledIssuerAccountNumber(issuer)
                .build();
    }

    @Test
    void matchesTheAccountThatEnrolledIt() {
        assertThat(enrolledUnder("SPN26241004").isEnrolledUnder("SPN26241004")).isTrue();
    }

    @Test
    void doesNotMatchAnotherAccount() {
        // A platform-enrolled seller against a company: this is the case that must be refused.
        assertThat(enrolledUnder("SPN26237883").isEnrolledUnder("SPN26241004")).isFalse();
    }

    @Test
    void isNotEnrolledUnderAnythingWhenTheIssuerIsUnknown() {
        // Null means "we never recorded it", which cannot be assumed to match anyone.
        assertThat(enrolledUnder(null).isEnrolledUnder("SPN26241004")).isFalse();
    }

    @Test
    void ignoresSurroundingWhitespaceAndCase() {
        assertThat(enrolledUnder("spn26241004 ").isEnrolledUnder("SPN26241004")).isTrue();
    }

    @Test
    void isNotEnrolledUnderABlankAccount() {
        assertThat(enrolledUnder("SPN26241004").isEnrolledUnder("  ")).isFalse();
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.entity.xtrm.PartnerRedemptionIssuerTest"`
Expected: FAIL — compilation error, no such builder method.

- [ ] **Step 4: Add the field and the check**

In `PartnerRedemption`, after `enrolledAt`:

```java
/**
 * The XTRM account that called {@code Register/CreateUser} for this seller.
 *
 * <p>XTRM binds a user to whoever created them, and refuses a second user with the same email — so this is
 * permanent and cannot be corrected by re-enrolling. A company may only pay a seller whose PAT it created
 * itself; the platform may pay any of them.</p>
 *
 * <p>NULL means the seller was enrolled before company-scoped enrollment existed — by the platform,
 * because that is all the code ever did. Left NULL rather than backfilled: the platform's account
 * number is environment-specific, and "not this company's" is the only thing any caller asks.</p>
 */
@Column(name = "enrolled_issuer_account_number", length = 50)
private String enrolledIssuerAccountNumber;

/** True when this seller's PAT was created by {@code issuerAccountNumber}. */
public boolean isEnrolledUnder(String issuerAccountNumber) {
    if (enrolledIssuerAccountNumber == null || issuerAccountNumber == null
            || issuerAccountNumber.isBlank()) {
        return false;
    }
    return enrolledIssuerAccountNumber.trim().equalsIgnoreCase(issuerAccountNumber.trim());
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew test --tests "com.tenxengage.app.entity.xtrm.PartnerRedemptionIssuerTest"`
Expected: PASS

- [ ] **Step 6: Write the migration**

Create `src/main/resources/db/migration/V59__record_enrollment_issuer.sql`:

```sql
-- Which XTRM account enrolled each seller.
--
-- WHY THIS IS PERMANENT
--
-- XTRM binds a user to whoever called Register/CreateUser for them, and refuses a second user with the same
-- email address ("Email Already Exists") even under a different issuer. So a seller's issuer cannot be
-- changed by re-enrolling them — this column records a fact, not a preference.
--
-- A partner company may only pay sellers it created itself. The platform may pay any of them, which is why
-- personal redemption is unaffected by this change.
--
-- WHY EXISTING ROWS ARE LEFT NULL
--
-- Every existing row was enrolled by the platform, because that is the only thing the code has ever done.
-- It is tempting to backfill them with the platform's account number, but that value comes from
-- XTRM_ISSUER_ACCOUNT and differs between environments — a literal here would be correct in one and wrong
-- in the others, silently.
--
-- It is also unnecessary. The only question this column answers is "did THIS company enrol this seller?",
-- and for a legacy row the answer is no however it is stored. NULL therefore means "enrolled before
-- company-scoped enrollment existed" and is refused on the vendor rails, which is the same outcome a
-- correct backfill would produce — without depending on deployment configuration being guessed right here.

ALTER TABLE partner_redemption
    ADD COLUMN enrolled_issuer_account_number VARCHAR(50);

COMMENT ON COLUMN partner_redemption.enrolled_issuer_account_number IS
    'XTRM account that created this seller''s PAT. Permanent — XTRM refuses a second user with the same '
    'email, so a seller cannot be re-enrolled under a different issuer. NULL means enrolled by the platform '
    'before company-scoped enrollment existed, and cannot receive company payouts.';

-- Reading this per recipient is on the distribution eligibility path.
CREATE INDEX idx_partner_redemption_issuer
    ON partner_redemption (enrolled_issuer_account_number);
```

**No backfill, deliberately.** The platform's account number is environment-specific, so a literal here would be right in one environment and quietly wrong in the others. `NULL` already carries the meaning that matters.

- [ ] **Step 7: Run the full suite and commit**

Run: `./gradlew test`
Expected: PASS at 1737 (1732 + 5 new).

```bash
git add src/main/resources/db/migration/V59__record_enrollment_issuer.sql \
        src/main/java/com/tenxengage/app/entity/xtrm/PartnerRedemption.java \
        src/test/java/com/tenxengage/app/entity/xtrm/PartnerRedemptionIssuerTest.java
git commit -m "feat(xtrm): record which account enrolled each seller"
```

---

## Task 2: `createUser` as a specific account

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java`
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateUserIssuerTest.java`

**Interfaces:**
- Consumes: `XtrmCredentials`, `XtrmApiClientImpl.platformCredentials()`.
- Produces: `CreateUserResult createUser(CreateUserCommand command, XtrmCredentials credentials)`. The existing single-argument form remains and delegates to platform credentials.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateUserIssuerTest.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which account creates the user.
 *
 * <p>This is the whole feature in one field: XTRM binds the new user to the {@code IssuerAccountNumber} in
 * the request, and to the account the bearer token belongs to. Send the platform's and the seller can never
 * be paid by their company — permanently, because the email cannot be reused.</p>
 */
class XtrmApiClientImplCreateUserIssuerTest {

    private String capturedIssuer;
    private XtrmCredentials capturedCredentials;

    private XtrmApiClientImpl clientCapturing() {
        return new XtrmApiClientImpl() {
            @Override
            protected Map<?, ?> post(String path, Map<String, Object> body, XtrmCredentials credentials) {
                Map<?, ?> outer = (Map<?, ?>) body.get("CreateUser");
                Map<?, ?> request = (Map<?, ?>) outer.get("request");
                capturedIssuer = String.valueOf(request.get("IssuerAccountNumber"));
                capturedCredentials = credentials;
                return Map.of("CreateUserResponse", Map.of("CreateUserResult", Map.of(
                        "UserID", "PAT26241022",
                        "AccountIdentityLevel", "Basic",
                        "OperationStatus", Map.of("Success", true, "Errors", List.of()))));
            }
        };
    }

    private CreateUserCommand command() {
        return new CreateUserCommand("Probe", "Seller", "probe@acme.test", "4085556247", "US",
                "1 Market St", null, "San Francisco", "CA", "94105", "US");
    }

    @Test
    void sendsTheSuppliedAccountAsIssuer() {
        XtrmCredentials company =
                new XtrmCredentials("company-id", "secret", "SPN26241004", "206415", "2314");

        XtrmApiClientImpl client = clientCapturing();
        client.createUser(command(), company);

        // Both must be the company's: the account named in the body, and the token the call is made with.
        assertThat(capturedIssuer).isEqualTo("SPN26241004");
        assertThat(capturedCredentials).isEqualTo(company);
    }

    @Test
    void returnsThePatFromTheResponse() {
        XtrmCredentials company =
                new XtrmCredentials("company-id", "secret", "SPN26241004", "206415", "2314");

        assertThat(clientCapturing().createUser(command(), company).recipientUserId())
                .isEqualTo("PAT26241022");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmApiClientImplCreateUserIssuerTest"`
Expected: FAIL — no two-argument `createUser`.

- [ ] **Step 3: Declare the overload**

In `XtrmApiClient.java`, beneath the existing `createUser`:

```java
/**
 * Enroll a payee as a specific account.
 *
 * <p>XTRM binds the new user to whichever account creates them, and will not create a second user with the
 * same email — so the account chosen here is permanent for that person. A seller must be created by their
 * own partner company for that company to be able to pay them.</p>
 */
CreateUserResult createUser(CreateUserCommand command, XtrmCredentials credentials);
```

- [ ] **Step 4: Implement in `XtrmApiClientImpl`**

Change the existing method to delegate, and move the body into the overload. The two lines that change inside the body are the issuer and the `post`:

```java
@Override
public CreateUserResult createUser(CreateUserCommand cmd) {
    return createUser(cmd, platformCredentials());
}

@Override
public CreateUserResult createUser(CreateUserCommand cmd, XtrmCredentials credentials) {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("AddressLine1", cmd.addressLine1());
    putIfPresent(address, "AddressLine2", cmd.addressLine2());
    putIfPresent(address, "City", cmd.city());
    putIfPresent(address, "Region", cmd.region());
    putIfPresent(address, "PostalCode", cmd.postalCode());
    address.put("CountryISO2", cmd.countryIso2());

    Map<String, Object> request = new LinkedHashMap<>();
    // The binding field. It must agree with the token below, or XTRM binds the user to the wrong account.
    request.put("IssuerAccountNumber", credentials.issuerAccountNumber());
    request.put("LegalFirstName", cmd.firstName());
    request.put("LegalLastName", cmd.lastName());
    request.put("EmailAddress", cmd.email());
    request.put("EmailNotification", "true");
    putIfPresent(request, "MobilePhone", PhoneDialCodes.mobilePhone(cmd.phoneCountryIso2(), cmd.phone()));
    request.put("Address", address);

    Map<String, Object> body = envelope("CreateUser", request);

    log.info("[step=xtrm_enroll] calling CreateUser as issuer={}", credentials.issuerAccountNumber());
    Map<?, ?> response;
    try {
        response = post("/API/v4/Register/CreateUser", body, credentials);
    } catch (RuntimeException e) {
        log.warn("[step=xtrm_enroll_failed] transport error calling CreateUser: {}", e.getClass().getSimpleName());
        return CreateUserResult.failed(List.of("Could not reach XTRM"), true);
    }
    // ... existing unwrap/parse body unchanged ...
}
```

- [ ] **Step 5: Implement the stub overload**

In `XtrmApiClientStub`, beside the existing `createUser`:

```java
@Override
public CreateUserResult createUser(CreateUserCommand cmd, XtrmCredentials credentials) {
    // Derived from the issuer as well as the email, so a stubbed run shows that different accounts
    // produce different PATs — which is the property this feature turns on.
    String pat = "PAT-STUB-" + token(credentials.issuerAccountNumber() + cmd.email());
    log.info("[stub] XTRM CreateUser as issuerAccount={} -> {}", credentials.issuerAccountNumber(), pat);
    return CreateUserResult.ok(pat, "Standard");
}
```

- [ ] **Step 6: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmApiClientImplCreateUserIssuerTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS. If `XtrmEnrollmentServiceTest` fails, it stubs the single-argument `createUser` and the code now calls it via delegation — that path is unchanged, so investigate rather than assuming.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClient.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientImpl.java \
        src/main/java/com/tenxengage/app/service/xtrm/XtrmApiClientStub.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmApiClientImplCreateUserIssuerTest.java
git commit -m "feat(xtrm): create a payee as a specific account, not always the platform"
```

---

## Task 3: Decide which account should enrol a seller

The rule in one place, so enrollment and eligibility cannot disagree about it — the same reason `XtrmRemitterResolver` exists for payouts.

**Files:**
- Create: `src/main/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolver.java`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolverTest.java`

**Interfaces:**
- Consumes: `XtrmCredentialsResolver.platform()`, `.forCompany(UUID, UUID)`, `.canPayFromOwnWallet(UUID, UUID)`.
- Produces:

```java
sealed interface EnrollmentIssuer {
    record UseAccount(XtrmCredentials credentials) implements EnrollmentIssuer {}
    record Defer(String reason) implements EnrollmentIssuer {}
}

EnrollmentIssuer resolve(UUID clientId, UUID partnerCompanyId);
```

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolverTest.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which XTRM account should create this seller.
 *
 * <p>The decision is irreversible: XTRM will not create a second user with the same email, so enrolling
 * under the wrong account excludes that seller from their company's distributions permanently. Deferring
 * costs a delay; guessing costs the seller.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerEnrollmentIssuerResolverTest {

    @Mock private XtrmCredentialsResolver credentialsResolver;
    @InjectMocks private SellerEnrollmentIssuerResolver resolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    private final XtrmCredentials platform =
            new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    @Test
    void usesTheCompanyWhenItIsConnected() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(true);
        when(credentialsResolver.forCompany(CLIENT_ID, COMPANY_ID)).thenReturn(company);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        assertThat(result).isInstanceOf(EnrollmentIssuer.UseAccount.class);
        assertThat(((EnrollmentIssuer.UseAccount) result).credentials()).isEqualTo(company);
    }

    @Test
    void defersWhenTheCompanyIsNotConnectedYet() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        // Falling back to the platform here is the one thing that must never happen: it would look like
        // success and permanently exclude this seller from their company's distributions.
        assertThat(result).isInstanceOf(EnrollmentIssuer.Defer.class);
        verify(credentialsResolver, never()).platform();
    }

    @Test
    void explainsWhyItDeferred() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        assertThat(((EnrollmentIssuer.Defer) result).reason()).containsIgnoringCase("not connected");
    }

    @Test
    void usesThePlatformForASellerWithNoCompany() {
        when(credentialsResolver.platform()).thenReturn(platform);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, null);

        // A user with no partner company can never be a distribution recipient, so there is nothing to
        // lose by enrolling them under the platform — and personal redemption needs them enrolled.
        assertThat(((EnrollmentIssuer.UseAccount) result).credentials()).isEqualTo(platform);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolverTest"`
Expected: FAIL — the class does not exist.

- [ ] **Step 3: Implement**

Create `src/main/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolver.java`:

```java
package com.tenxengage.app.service.xtrm;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Which XTRM account should create a given seller.
 *
 * <p>XTRM binds a user to whoever creates them and refuses a second user with the same email, so this
 * decision cannot be revisited. Enrolling a partner company's seller under the platform would look like
 * success and exclude that seller from their company's distributions forever — which is why there is no
 * fallback here, only {@link EnrollmentIssuer.Defer}.</p>
 */
@Service
public class SellerEnrollmentIssuerResolver {

    /** Either an account to enrol as, or a reason to wait. There is deliberately no third option. */
    public sealed interface EnrollmentIssuer {
        record UseAccount(XtrmCredentials credentials) implements EnrollmentIssuer { }

        record Defer(String reason) implements EnrollmentIssuer { }
    }

    private final XtrmCredentialsResolver credentialsResolver;

    public SellerEnrollmentIssuerResolver(XtrmCredentialsResolver credentialsResolver) {
        this.credentialsResolver = credentialsResolver;
    }

    public EnrollmentIssuer resolve(UUID clientId, UUID partnerCompanyId) {
        if (partnerCompanyId == null) {
            // No company means this person can never be a distribution recipient, so nothing is lost by
            // enrolling them under the platform — and personal redemption needs them enrolled at all.
            return new EnrollmentIssuer.UseAccount(credentialsResolver.platform());
        }
        if (!credentialsResolver.canPayFromOwnWallet(clientId, partnerCompanyId)) {
            return new EnrollmentIssuer.Defer(
                    "This seller's company is not connected to XTRM yet.");
        }
        return new EnrollmentIssuer.UseAccount(
                credentialsResolver.forCompany(clientId, partnerCompanyId));
    }
}
```

- [ ] **Step 4: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolverTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolver.java \
        src/test/java/com/tenxengage/app/service/xtrm/SellerEnrollmentIssuerResolverTest.java
git commit -m "feat(xtrm): decide which account enrols a seller, with no platform fallback"
```

---

## Task 4: Enrol under the company

The behavioural change. After this, a new seller in a connected company is created by that company.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentService.java:142-182`
- Test: `src/test/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentIssuerTest.java`

**Interfaces:**
- Consumes: Task 2's `createUser(cmd, credentials)`, Task 3's `resolve(...)`, Task 1's `setEnrolledIssuerAccountNumber(...)`.
- Produces: no new public signatures. `XtrmEnrollmentService`'s constructor gains `SellerEnrollmentIssuerResolver issuerResolver` as its fifth parameter.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentIssuerTest.java`:

```java
package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerAddress;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer;
import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enrolling a seller under their own company.
 *
 * <p>The property that matters is what happens when the company is <em>not</em> ready. Enrolling under the
 * platform then would succeed, look correct, and permanently exclude the seller from company distributions
 * — XTRM will not create a second user with the same email. So "not ready" must produce nothing at all.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmEnrollmentIssuerTest {

    @Mock private PartnerRedemptionRepository redemptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private AuditLogService auditLogService;
    @Mock private SellerEnrollmentIssuerResolver issuerResolver;

    private XtrmEnrollmentService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    @BeforeEach
    void setUp() {
        service = new XtrmEnrollmentService(redemptionRepository, userRepository, xtrmApiClient,
                auditLogService, issuerResolver);

        User user = new User();
        user.setId(USER_ID);
        user.setClientId(CLIENT_ID);
        user.setPartnerCompanyId(COMPANY_ID);
        user.setFirstName("Probe");
        user.setLastName("Seller");
        user.setEmail("probe@acme.test");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .address(PartnerAddress.builder().line1("1 Market St").countryIso2("US").build())
                .build();
        when(redemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(profile));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enrolsUnderTheCompanyAndRecordsIt() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.UseAccount(company));
        when(xtrmApiClient.createUser(any(), any())).thenReturn(CreateUserResult.ok("PAT26241022", "Basic"));

        service.enrollIfNeeded(USER_ID);

        verify(xtrmApiClient).createUser(any(), org.mockito.ArgumentMatchers.eq(company));

        ArgumentCaptor<PartnerRedemption> saved = ArgumentCaptor.forClass(PartnerRedemption.class);
        verify(redemptionRepository).save(saved.capture());
        assertThat(saved.getValue().getRecipientUserId()).isEqualTo("PAT26241022");
        assertThat(saved.getValue().getEnrolledIssuerAccountNumber()).isEqualTo("SPN26241004");
        assertThat(saved.getValue().getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.ENROLLED);
    }

    @Test
    void createsNobodyWhenTheCompanyIsNotConnected() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.Defer("This seller's company is not connected to XTRM yet."));

        service.enrollIfNeeded(USER_ID);

        // The single most important assertion in this change: no call, under any account.
        verify(xtrmApiClient, never()).createUser(any());
        verify(xtrmApiClient, never()).createUser(any(), any());
    }

    @Test
    void leavesTheProfileUnenrolledWhenDeferring() {
        when(issuerResolver.resolve(CLIENT_ID, COMPANY_ID))
                .thenReturn(new EnrollmentIssuer.Defer("This seller's company is not connected to XTRM yet."));

        service.enrollIfNeeded(USER_ID);

        ArgumentCaptor<PartnerRedemption> saved = ArgumentCaptor.forClass(PartnerRedemption.class);
        verify(redemptionRepository).save(saved.capture());
        // NOT_ENROLLED, not FAILED: nothing went wrong and a retry will succeed once the company connects.
        assertThat(saved.getValue().getEnrollmentStatus()).isEqualTo(XtrmEnrollmentStatus.NOT_ENROLLED);
        assertThat(saved.getValue().getEnrollmentError()).containsIgnoringCase("not connected");
        assertThat(saved.getValue().getRecipientUserId()).isNull();
    }

    @Test
    void recordsThePlatformForASellerWithNoCompany() {
        User noCompany = new User();
        noCompany.setId(USER_ID);
        noCompany.setClientId(CLIENT_ID);
        noCompany.setEmail("solo@acme.test");
        noCompany.setFirstName("Solo");
        noCompany.setLastName("User");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(noCompany));

        XtrmCredentials platform =
                new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
        when(issuerResolver.resolve(CLIENT_ID, null))
                .thenReturn(new EnrollmentIssuer.UseAccount(platform));
        when(xtrmApiClient.createUser(any(), any())).thenReturn(CreateUserResult.ok("PAT9999", "Basic"));

        service.enrollIfNeeded(USER_ID);

        ArgumentCaptor<PartnerRedemption> saved = ArgumentCaptor.forClass(PartnerRedemption.class);
        verify(redemptionRepository).save(saved.capture());
        assertThat(saved.getValue().getEnrolledIssuerAccountNumber()).isEqualTo("SPN26237883");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmEnrollmentIssuerTest"`
Expected: FAIL — the constructor takes four arguments, not five.

- [ ] **Step 3: Add the collaborator**

```java
private final SellerEnrollmentIssuerResolver issuerResolver;

public XtrmEnrollmentService(PartnerRedemptionRepository userRedemptionRepository,
                             UserRepository userRepository,
                             XtrmApiClient xtrmApiClient,
                             AuditLogService auditLogService,
                             SellerEnrollmentIssuerResolver issuerResolver) {
    this.userRedemptionRepository = userRedemptionRepository;
    this.userRepository = userRepository;
    this.xtrmApiClient = xtrmApiClient;
    this.auditLogService = auditLogService;
    this.issuerResolver = issuerResolver;
}
```

- [ ] **Step 4: Resolve the issuer before calling XTRM**

In `enrollIfNeeded(User)`, between the address check and the `createUser` call:

```java
        // Which account creates this seller decides, permanently, who can pay them. XTRM refuses a second
        // user with the same email, so there is no correcting this later.
        EnrollmentIssuer issuer = issuerResolver.resolve(user.getClientId(), user.getPartnerCompanyId());
        if (issuer instanceof EnrollmentIssuer.Defer defer) {
            // Deliberately NOT markFailed: nothing failed, and a retry once the company connects will
            // succeed. FAILED would read as a problem with the seller and invite a manual "fix".
            profile.setEnrollmentError(defer.reason());
            userRedemptionRepository.save(profile);
            log.info("[step=xtrm_enroll_deferred] userId={} reason=company_not_connected", user.getId());
            return;
        }
        XtrmCredentials credentials = ((EnrollmentIssuer.UseAccount) issuer).credentials();
```

Then change the vendor call and the success branch:

```java
            result = xtrmApiClient.createUser(new CreateUserCommand(
                    user.getFirstName(), user.getLastName(), user.getEmail(),
                    user.getPhone(), user.getPhoneCountryIso2(),
                    addr.getLine1(), addr.getLine2(), addr.getCity(),
                    addr.getRegion(), addr.getPostalCode(), addr.getCountryIso2()), credentials);
```

```java
        if (result.success()) {
            profile.setRecipientUserId(result.recipientUserId());
            profile.setIdentityLevel(result.identityLevel());
            profile.setEnrolledIssuerAccountNumber(credentials.issuerAccountNumber());
            profile.setEnrollmentStatus(XtrmEnrollmentStatus.ENROLLED);
```

Add imports: `com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer`.

- [ ] **Step 5: Run the test, fix construction sites, run everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.xtrm.XtrmEnrollmentIssuerTest"`
Expected: PASS

Run: `grep -rn "new XtrmEnrollmentService(" src/ --include=*.java` and add the fifth argument to each. `XtrmEnrollmentServiceTest` exists and will need the mock; stub `issuerResolver.resolve(any(), any())` to return `new EnrollmentIssuer.UseAccount(platformCredentials)` so its existing cases keep testing what they were written to test.

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentService.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentIssuerTest.java \
        src/test/java/com/tenxengage/app/service/xtrm/XtrmEnrollmentServiceTest.java
git commit -m "feat(xtrm): enrol a seller under their own company, or not at all"
```

---

## Task 5: Refuse a platform-bound seller on the vendor rails

Without this, a company distribution to a legacy seller reaches XTRM and fails there, after funds are reserved.

**Files:**
- Modify: `src/main/java/com/tenxengage/app/service/DistributionRecipientService.java`
- Test: `src/test/java/com/tenxengage/app/service/DistributionRecipientIssuerMismatchTest.java`

**Interfaces:**
- Consumes: `PartnerRedemption.isEnrolledUnder(String)` from Task 1.
- Produces: `XtrmCredentialsResolver.companyIssuerAccountNumber(UUID clientId, UUID partnerCompanyId)` returning `Optional<String>`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/tenxengage/app/service/DistributionRecipientIssuerMismatchTest.java`:

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
 * A seller enrolled under the platform cannot be paid by their company.
 *
 * <p>XTRM binds a user to whoever created them, and refuses to create them again under another account —
 * so this is permanent for everyone enrolled before company-scoped enrollment existed. Refusing on the
 * listing is the only honest option: the alternative is reserving the money and failing at the vendor.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientIssuerMismatchTest {

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

    private void sellerEnrolledUnder(String issuer) {
        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26240089")
                .enrolledIssuerAccountNumber(issuer)
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(profile));
    }

    private DistributionRecipientResponse only(DistributionRail rail) {
        List<DistributionRecipientResponse> out = service.listRecipients(CLIENT_ID, COMPANY_ID, rail);
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    @Test
    void refusesASellerEnrolledUnderThePlatform() {
        sellerEnrolledUnder("SPN26237883");

        DistributionRecipientResponse row = only(DistributionRail.GIFT_CARD);

        assertThat(row.eligible()).isFalse();
        assertThat(row.ineligibleReason()).containsIgnoringCase("cannot receive");
    }

    @Test
    void acceptsASellerEnrolledUnderThisCompany() {
        sellerEnrolledUnder("SPN26241004");

        assertThat(only(DistributionRail.GIFT_CARD).eligible()).isTrue();
    }

    @Test
    void stillAllowsWalletCreditForAPlatformBoundSeller() {
        sellerEnrolledUnder("SPN26237883");

        // WALLET_CREDIT never touches XTRM, so who enrolled the seller is irrelevant to it. This is what
        // keeps legacy sellers reachable at all.
        assertThat(only(DistributionRail.WALLET_CREDIT).eligible()).isTrue();
    }

    @Test
    void refusesASellerWhoseIssuerWasNeverRecorded() {
        sellerEnrolledUnder(null);

        // Unknown is not the same as ours. Guessing here would reserve money for a payout XTRM rejects.
        assertThat(only(DistributionRail.GIFT_CARD).eligible()).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipientIssuerMismatchTest"`
Expected: FAIL — `refusesASellerEnrolledUnderThePlatform` passes no check yet.

- [ ] **Step 3: Implement**

In `evaluate(...)`, immediately after the existing company-connected check and before the per-rail `switch`:

```java
        // Even with the company connected, XTRM will only let it pay sellers it created itself. Sellers
        // enrolled before company-scoped enrollment existed are bound to the platform, and cannot be
        // re-enrolled — XTRM refuses a second user with the same email. Refusing here is the only honest
        // option; the alternative reserves the money and fails at the vendor.
        if (rail.isVendorPayout()) {
            String companyIssuer = credentialsResolver
                    .companyIssuerAccountNumber(clientId, companyId).orElse(null);
            if (profile == null || !profile.isEnrolledUnder(companyIssuer)) {
                return Eligibility.no(
                        "This seller cannot receive company payouts — use Wallet Transfer instead");
            }
        }
```

**Not `forCompany`.** That decrypts the credential blob, and `evaluate` runs once per seller — listing a
company with 200 sellers would decrypt its secrets 200 times to read a public identifier. Add a reader that
touches only the clear columns:

```java
/**
 * The company's XTRM account number, without decrypting anything.
 *
 * <p>{@link #forCompany} would also answer this, but it decrypts the credential blob to do so. The account
 * number is an identifier, not a secret, and callers on listing paths ask for it once per row.</p>
 */
@Transactional(readOnly = true)
public Optional<String> companyIssuerAccountNumber(UUID clientId, UUID partnerCompanyId) {
    return accountRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId)
            .map(PartnerCompanyXtrmAccount::getXtrmAccountNumber);
}
```

- [ ] **Step 4: Run the test, then everything**

Run: `./gradlew test --tests "com.tenxengage.app.service.DistributionRecipient*"`
Expected: PASS. `DistributionRecipientCompanyConnectionTest` and `DistributionRecipientServiceRailSwitchTest` build profiles without an issuer, so they will now report ineligible on vendor rails — set `.enrolledIssuerAccountNumber("SPN26241004")` on their fixtures and stub `companyIssuerAccountNumber` to match, so each keeps testing the rule it was written for.

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tenxengage/app/service/DistributionRecipientService.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientIssuerMismatchTest.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientCompanyConnectionTest.java \
        src/test/java/com/tenxengage/app/service/DistributionRecipientServiceRailSwitchTest.java
git commit -m "fix(distribution): refuse a seller their company cannot pay, before money is reserved"
```

---

## After the plan

**Verify in `local` against the XTRM sandbox:**

1. Create a company, connect it, then complete a seller's profile in it. Confirm `partner_redemption.enrolled_issuer_account_number` is the **company's** SPN, not the platform's.
2. Complete a seller's profile in an **unconnected** company. Confirm no XTRM user is created, status stays `NOT_ENROLLED`, and `enrollment_error` explains why.
3. Connect that company, trigger enrollment again, confirm it now succeeds under the company.
4. `OTP/GetConnectedStatus` as the company against the new PAT → expect `Connected`.

**Known limitation to communicate, not fix.** Every seller enrolled before this ships is permanently unable to receive company distributions. `WALLET_CREDIT` reaches them; the XTRM rails never will. The only escape is XTRM support releasing the email, which is outside our control.

**One shared platform account, confirmed 2026-08-25.** Every client uses the same TenXEngage XTRM client id and secret; only partner companies have their own. So `XtrmCredentialsResolver.platform()` staying a single global value is correct, and no per-client XTRM identity is needed.

**Still outstanding from the parent design:** funding moves no money at XTRM (D-8). Company-remitted payouts cannot succeed in production until `TransferFundToCompany` is wired into `POST /wallets/company/{id}/fund`.
